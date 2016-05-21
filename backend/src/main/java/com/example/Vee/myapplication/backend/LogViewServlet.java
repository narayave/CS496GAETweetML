package com.example.Vee.myapplication.backend;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.jdo.PersistenceManager;
import javax.servlet.http.*;

@SuppressWarnings("serial")
public class LogViewServlet extends HttpServlet {

	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.setContentType("text/html");
		PrintWriter out = resp.getWriter();
		PersistenceManager pm = PMF.getPMF().getPersistenceManager();
		try {
			List<Log> logs = Log.loadRecent(pm);
			out.write("<ul>");
			for (Log log : logs) {
				out.write("<li>" + (log.getWhen() + ":" + log.getInfo()).replaceAll("<", "&lt;"));
				out.write("<pre>" + (log.getDetails() + "\n\n").replaceAll("<", "&lt;") + "</pre>");
			}
			out.write("</ul>");
		} finally {
			pm.close();
		}
	}
}
