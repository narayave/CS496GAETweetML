package com.example.Vee.myapplication.backend;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.jdo.PersistenceManager;
import javax.servlet.http.*;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;

import weka.classifiers.Classifier;
import weka.classifiers.functions.Logistic;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

@SuppressWarnings("serial")
public class DailyDigestServlet extends HttpServlet {
	// break the download-and-train process into steps
	// to increase the amount of time that GAE allows;
	// could break into even more fine-grained steps as needed
	final static String STEP_1_DOWNLOAD = "download";
	final static List<String> DOWNLOADEES = MachineLearningModel.TWITTER_ACCOUNTS;
	final static String STEP_2_PAUSE = "pause";
	final static String STEP_3_TRAIN = "train";

	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		doPost(req, resp);
	}

	public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String step = req.getParameter("op");
		if (step == null)
			step = STEP_1_DOWNLOAD;

		PersistenceManager pm = PMF.getPMF().getPersistenceManager();
		try {
			switch (step) {
			case STEP_1_DOWNLOAD:
				step1download(req, resp, pm);
				break;
			case STEP_2_PAUSE:
				step2Pause(req, resp, pm);
				break;
			case STEP_3_TRAIN:
				step3train(req, resp, pm);
				break;
			}
		} catch (Exception ex) {
			Log.record("ERROR", ex.toString(), pm);
		} finally {
			pm.close();
		}
	}

	private void step1download(HttpServletRequest req, HttpServletResponse resp, PersistenceManager pm)
			throws Exception {
		for (String downloadee : DOWNLOADEES) {
			Log.record(STEP_1_DOWNLOAD, "Retrieving " + downloadee, pm);
			List<String> download = TwitterService.download(downloadee);
			Tweet.storeAll(downloadee, download, pm);
			Log.record(STEP_1_DOWNLOAD, "Saved " + download.size() + " tweets for " + downloadee, pm);
		}
		Log.record(STEP_1_DOWNLOAD, "request pause task", pm);
		Queue queue = QueueFactory.getDefaultQueue();
		queue.add(TaskOptions.Builder.withUrl(req.getRequestURI()).param("op", STEP_2_PAUSE));
	}
	


	private void step2Pause(HttpServletRequest req, HttpServletResponse resp, PersistenceManager pm) throws InterruptedException {
		Log.record(STEP_2_PAUSE, "pausing to let datastore settle", pm);
		Thread.sleep(10000);
		Log.record(STEP_2_PAUSE, "request retrain task", pm);
		Queue queue = QueueFactory.getDefaultQueue();
		queue.add(TaskOptions.Builder.withUrl(req.getRequestURI()).param("op", STEP_3_TRAIN));
	}

	private void step3train(HttpServletRequest req, HttpServletResponse resp, PersistenceManager pm) throws Exception {
		Log.record(STEP_3_TRAIN, "train requested", pm);
		Map<String, List<Tweet>> allTweets = Tweet.loadAndGroup(pm);

		// build up a list of all known words
		Set<String> allWords = new TreeSet<String>();
		int ntweets = 0;
		for (String downloadee : allTweets.keySet()) {
			for (Tweet tweet : allTweets.get(downloadee)) {
				Map<String, Integer> digest = tweet.getDigest();
				allWords.addAll(digest.keySet());
			}
			ntweets += allTweets.get(downloadee).size();
		}
		Log.record(STEP_3_TRAIN, ntweets + " tweets available for training", pm);

		// convert to Weka model
		ArrayList<Attribute> attrs = new ArrayList<Attribute>();
		Map<String, Integer> wordToAttrPosition = new HashMap<String, Integer>();
		for (String word : allWords) {
			String attrName = uniqueify(word);
			Attribute attr = new Attribute(attrName);
			wordToAttrPosition.put(word, attrs.size());
			attrs.add(attr);
		}

		// add the training specification
		attrs.add(new Attribute("downloadee", DOWNLOADEES));
		Log.record(STEP_3_TRAIN, attrs.size() + " attributes for training (including target)", pm);

		// construct the instances
		Instances data = new Instances("tweets", attrs, ntweets);
		Map<Instance, Tweet> mapBackToSource = new HashMap<Instance, Tweet>();
		for (String downloadee : allTweets.keySet()) {
			for (Tweet tweet : allTweets.get(downloadee)) {
				Instance instance = MachineLearningModel.tweetToInstance(tweet, attrs, wordToAttrPosition);
				instance.setDataset(data);
				data.add(instance);

				mapBackToSource.put(instance, tweet);
			}
		}
		Log.record(STEP_3_TRAIN, data.size() + " instances constructed", pm);

		// train model
		Classifier model = new Logistic();
		data.setClass(data.attribute("downloadee"));
		model.buildClassifier(data);
		System.err.println(model.toString());
		Log.record(STEP_3_TRAIN, "training complete", pm);
		MachineLearningModel mlm = MachineLearningModel.save(model, attrs, wordToAttrPosition, pm);

		// test model on its training data, to measure internal consistency
		int nright = 0, nwrong = 0;
		for (Instance instance : mapBackToSource.keySet()) {
			Tweet tweet = mapBackToSource.get(instance);
			String strAssignment = mlm.classify(tweet);
			String strActualTruth = DOWNLOADEES.get((int) instance.value(attrs.size() - 1));
			String strSourceTruth = tweet.getAccount();

			if (!strAssignment.equals(strActualTruth) || !strActualTruth.equals(strSourceTruth)) {
				nwrong++;
				String msg = "Guessed " + strAssignment;
				msg += " for " + strActualTruth + "/" + strSourceTruth;
				msg += " tweet '" + tweet.getContent() + "'.";
				System.err.println(msg);
			} else {
				nright++;
			}
		}
		float pct = nright + nwrong > 0 ? (100F * nright) / (float) (nright + nwrong) : 0F;
		Log.record(STEP_3_TRAIN, "got " + nright + " right and " + nwrong + " wrong (" + pct + "%)", pm);

	}

	private static int uniquifier = 0;

	/**
	 * Converts a given word into a unique Weka-compatible string resembling
	 * that word. The return value will contain at least one underscore.
	 */
	private static String uniqueify(String word) {
		StringBuffer rv = new StringBuffer();
		rv.append("A");
		rv.append(Integer.toString(uniquifier++));
		rv.append("_");
		
		for (int i = 0; i < word.length(); i++) {
			char ch = word.charAt(i);
			if (ch >= 'a' && ch <= 'z')
				rv.append(ch);
			else if (ch >= '0' && ch <= '9')
				rv.append(ch);
			else if (ch == '@')
				rv.append("_AT_");
			else if (ch == '#')
				rv.append("_HT_");
			else if (ch == '!')
				rv.append("_EX_");
			else if (ch == '?')
				rv.append("_QM_");
		}
		return rv.toString();
	}

}
