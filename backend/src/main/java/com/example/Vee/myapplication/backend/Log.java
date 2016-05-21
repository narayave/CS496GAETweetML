package com.example.Vee.myapplication.backend;

import java.util.Date;
import java.util.List;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

import com.google.appengine.api.datastore.Text;

@PersistenceCapable
public class Log {

	public static List<Log> loadRecent(PersistenceManager pm) {
		Query query = pm.newQuery(Log.class);
		query.setOrdering("when desc");
		query.setRange(0, 100);
		@SuppressWarnings("unchecked")
		List<Log> rv = (List<Log>) query.execute();
		rv.size();
		query.closeAll();
		return rv;
	}

	public static void record(String info, String details, PersistenceManager pm) {
		Log log = new Log();
		log.setDetails(details);
		log.setInfo(info);
		log.setWhen(new java.util.Date());
		pm.makePersistent(log);
	}

	@PrimaryKey
	@Persistent(valueStrategy = IdGeneratorStrategy.IDENTITY)
	private Long id;

	@Persistent
	private Date when;

	@Persistent
	private String info;

	@Persistent
	private Text details;

	public String getDetails() {
		return details != null ? details.getValue() : "";
	}

	public Long getId() {
		return id;
	}

	public String getInfo() {
		return info;
	}

	public Date getWhen() {
		return when;
	}

	public void setDetails(String details) {
		this.details = new Text(details != null ? details : "");
	}

	public void setInfo(String info) {
		this.info = info;
	}

	public void setWhen(Date when) {
		this.when = when;
	}

}
