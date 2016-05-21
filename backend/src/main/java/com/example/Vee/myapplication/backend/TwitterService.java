package com.example.Vee.myapplication.backend;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.jdo.PersistenceManager;

import twitter4j.Paging;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.PropertyConfiguration;

public class TwitterService {
	private final static int NTWEETS = 200;
	private static Twitter TWITTER = null;
	static {
		try {
			FileInputStream fis = new FileInputStream("WEB-INF/twitter4j.properties");
			PropertyConfiguration authConfig = new PropertyConfiguration(fis);
			TwitterFactory twitterConnectionFactory = new TwitterFactory(authConfig);
			TWITTER = twitterConnectionFactory.getInstance();
		} catch (Exception e) {
			e.printStackTrace();
			PersistenceManager pm = PMF.getPMF().getPersistenceManager();
			try {
				Log.record("STARTUP", e.toString(), pm);
			} finally {
				pm.close();
			}
		}
	}

	public static List<String> download(String username) throws TwitterException {
		if (TWITTER == null)
			throw new IllegalStateException("Unable to connect to Twitter; check auth credentials.");

		List<Status> statusObjs = TWITTER.getUserTimeline(username, new Paging(1, NTWEETS));
		List<String> rv = new ArrayList<String>();
		for (Status status : statusObjs)
			if (status != null && status.getText() != null && status.getText().length() > 0)
				rv.add(status.getText().trim());
		return rv;
	}

}
