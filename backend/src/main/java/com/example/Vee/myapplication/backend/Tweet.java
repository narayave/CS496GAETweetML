package com.example.Vee.myapplication.backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

@PersistenceCapable
public class Tweet {
	public static Map<String, List<Tweet>> loadAndGroup(PersistenceManager pm) {
		Query query = pm.newQuery(Tweet.class);
		@SuppressWarnings("unchecked")
		List<Tweet> tmp = (List<Tweet>) query.execute();
		tmp.size();
		Map<String, List<Tweet>> rv = new HashMap<String, List<Tweet>>();
		for (Tweet tweet : tmp) {
			String account = tweet.getAccount();
			List<Tweet> list = rv.get(account);
			if (list == null) {
				list = new ArrayList<Tweet>();
				rv.put(account, list);
			}
			list.add(tweet);
		}
		query.closeAll();
		return rv;
	}

	public static void storeAll(String downloadee, List<String> download, PersistenceManager pm) {
		List<Tweet> list = new ArrayList<Tweet>();
		for (String msg : download) {
			Tweet tweet = new Tweet();
			tweet.setAccount(downloadee);
			tweet.setContent(msg);
			tweet.setDigest(computeDigest(tweet.getContent()));
			list.add(tweet);
		}
		pm.makePersistentAll(list);
	}

	@PrimaryKey
	@Persistent
	private String content;

	@Persistent
	private String account;

	@Persistent(serialized = "true")
	private Map<String, Integer> digest;

	public String getAccount() {
		return account != null ? account : "";
	}

	public String getContent() {
		return content != null ? content : "";
	}

	public Map<String, Integer> getDigest() {
		if (digest == null || digest.size() == 0)
			digest = computeDigest(getContent());
		return digest;
	}

	public void setAccount(String account) {
		this.account = account;
	}

	public void setContent(String content) {
		this.content = content != null ? content.replace('\n', ' ').replace('\r', ' ') : "";
	}

	public void setDigest(Map<String, Integer> digest) {
		this.digest = digest;
	}

	private static final Pattern WORDLIKE = Pattern.compile("([\\#\\@]?[a-zA-Z0-9\\'\\-�]+)|[\\?\\!]");

	private static Map<String, Integer> computeDigest(String msg) {
		Map<String, Integer> rv = new HashMap<String, Integer>();
		Matcher m = WORDLIKE.matcher(msg);
		while (m.find()) {
			String word = m.group().toLowerCase();
			Integer cnt = rv.get(word);
			if (cnt == null)
				rv.put(word, 1);
			else
				rv.put(word, cnt + 1);
		}
		return rv;
	}
}
