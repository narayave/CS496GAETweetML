package com.example.Vee.myapplication.backend;

import java.io.IOException;
import java.io.PrintWriter;

import javax.jdo.PersistenceManager;
import javax.servlet.http.*;

@SuppressWarnings("serial")
public class ClassifyTweetServlet extends HttpServlet {

	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.setContentType("application/json");

		PrintWriter out = resp.getWriter();
		PersistenceManager pm = PMF.getPMF().getPersistenceManager();
		try {
			String msg = req.getParameter("tweet");
			if (msg == null || msg.length() == 0)
				throw new IllegalArgumentException("Hey, you need to provide a 'tweet' parameter.");

			MachineLearningModel mlm = MachineLearningModel.load(pm);
			Tweet tweet = new Tweet();
			tweet.setContent(msg);
			String assignment = mlm.classify(tweet);
			out.write(Util.toJsonPair("result", assignment));
		} catch (Exception e) {
			out.write(Util.toJsonPair("errormsg", e + ""));
		} finally {
			pm.close();
		}
	}
}
