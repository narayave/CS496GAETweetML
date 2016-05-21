package com.example.Vee.myapplication.backend;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.annotations.NotPersistent;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

import com.google.appengine.api.datastore.Blob;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;

import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

@PersistenceCapable
public class MachineLearningModel implements Serializable {
	private static final long serialVersionUID = -6404930887349375207L;

	private static final String SINGLETON = "singleton";

	final public static List<String> TWITTER_ACCOUNTS = Arrays
			.asList(new String[] { "realDonaldTrump", "BernieSanders" });

	public static MachineLearningModel save(Classifier classifier, ArrayList<Attribute> attributes,
			Map<String, Integer> wordMap, PersistenceManager pm) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bos);
		oos.writeObject(classifier);
		oos.flush();
		byte[] model = bos.toByteArray();

		MachineLearningModel rv = new MachineLearningModel();
		rv.setModel(model);
		rv.setAttributes(attributes);
		rv.setWordMap(wordMap);
		rv.classifier = classifier;
		pm.makePersistent(rv);

		MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
		memcache.put(SINGLETON, rv);

		return rv;
	}

	public static MachineLearningModel load(PersistenceManager pm) throws Exception {

		MemcacheService memcache = MemcacheServiceFactory.getMemcacheService();
		MachineLearningModel rv = (MachineLearningModel) memcache.get(SINGLETON);

		if (rv == null)
			try {
				rv = pm.getObjectById(MachineLearningModel.class, SINGLETON);
			} catch (JDOObjectNotFoundException trainingNotYetDone) {
				throw new IllegalStateException("Model not yet trained. Come back tomorrow.");
			}

		byte[] model = rv.getModel();
		if (model == null || model.length == 0)
			throw new IllegalStateException("Untrained model. Try again later.");

		ByteArrayInputStream bis = new ByteArrayInputStream(model);
		ObjectInputStream ois = new ObjectInputStream(bis);
		rv.classifier = (Classifier) (ois.readObject());

		return rv;
	}

	static Instance tweetToInstance(Tweet tweet, ArrayList<Attribute> attrs, Map<String, Integer> wordToAttrPosition) {
		if (attrs == null || attrs.size() == 0)
			throw new IllegalArgumentException("Uninitialized machine learning features.");
		if (wordToAttrPosition == null || wordToAttrPosition.size() == 0)
			throw new IllegalArgumentException("Invalid assignment of words to machine learning attributes.");

		Map<String, Integer> digest = tweet.getDigest();
		double[] attrValues = new double[attrs.size()];

		for (String word : digest.keySet()) {
			Integer attrPos = wordToAttrPosition.get(word);
			if (attrPos == null)
				continue; // doesn't appear in the model
			int attrVal = digest.get(word);
			attrValues[attrPos] = attrVal;
		}
		// add the target label
		attrValues[attrs.size() - 1] = TWITTER_ACCOUNTS.indexOf(tweet.getAccount());

		Instance instance = new DenseInstance(1, attrValues);
		return instance;
	}

	@Persistent(serialized = "true")
	private Map<String, Integer> wordMap;

	@Persistent(serialized = "true")
	private List<Attribute> attributes;

	@PrimaryKey
	@Persistent
	private String key = SINGLETON;

	@Persistent
	private Blob model;

	@NotPersistent
	private Classifier classifier;

	public String classify(Tweet tweet) throws Exception {
		if (classifier == null)
			throw new IllegalStateException("Uninitialized classifier");
		Instance instance = MachineLearningModel.tweetToInstance(tweet, getAttributes(), getWordMap());
		Instances data = new Instances("tweets", getAttributes(), 1);
		instance.setDataset(data);
		data.setClassIndex(getAttributes().size() - 1);

		int assignment = (int) classifier.classifyInstance(instance);
		if (assignment < 0 || assignment >= TWITTER_ACCOUNTS.size())
			throw new IllegalStateException("Invalid state: attempted to classify with outdated classifier.");
		return TWITTER_ACCOUNTS.get(assignment);
	}

	private ArrayList<Attribute> getAttributes() {
		// weka requires an ArrayList, but we can't guarantee that GAE stores
		// the list as an ArrayList, so convert back if necessary
		if (attributes != null && attributes instanceof ArrayList)
			return ((ArrayList<Attribute>) attributes);
		else {
			ArrayList<Attribute> tmp = new ArrayList<Attribute>();
			if (attributes != null)
				tmp.addAll(attributes);
			attributes = tmp;
			return tmp;
		}
	}

	private byte[] getModel() {
		return model != null ? model.getBytes() : null;
	}

	private Map<String, Integer> getWordMap() {
		return wordMap != null ? wordMap : new HashMap<String, Integer>();
	}

	private void setAttributes(List<Attribute> attributes) {
		this.attributes = attributes;
	}

	public void setModel(byte[] model) {
		this.model = new Blob(model != null ? model : new byte[0]);
	}

	private void setWordMap(Map<String, Integer> wordMap) {
		this.wordMap = wordMap;
	}
}
