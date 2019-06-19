// MIT License
//
// Copyright (c) 2019 Marcos Cacabelos Prol
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package mcp.kiuwan.zendesk;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import mcp.kiuwan.jira.JiraClient;
import mcp.kiuwan.jira.JiraException;
import mcp.kiuwan.jira.beans.Issue;
import mcp.kiuwan.jira.beans.Status;
import mcp.kiuwan.jira.beans.Transition;
import mcp.kiuwan.zendesk.beans.Ticket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZClient {
	
	private final static String CONFIG_FILE = "config.properties";
	private static Logger logger = LoggerFactory.getLogger(ZClient.class);
	
	private Properties config;
	private ZendeskClient zdClient;
	private JiraClient jiraClient;

	
	public static void main(String[] args) throws Exception {
		logger.info("Running {} ...", ZClient.class.getName());
		
		ZClient zclient = new ZClient();	
		
		String option = (args.length>0 ? args[0] : null);
				
		if ("--release".equalsIgnoreCase(option)) {
			zclient.release();		
		} else if ("--showtickets".equalsIgnoreCase(option)) {
			zclient.getTicketsWithIssueFromView(zclient.getProperty("zendesk.view"));
		} else if ("--showissues".equalsIgnoreCase(option)) {
			zclient.getJiraIssues(Arrays.asList(zclient.getProperty("release.jira.issues").trim().split("[\\s]+")));
		} else if ("--showreleasedtickets".equalsIgnoreCase(option)) {
			zclient.showReleasedTickets();
		} else {
			help();
		}

		logger.info("... end.");
	}


	private static void help() {
		System.out.println("Options:");
		System.out.println("   --release");
		System.out.println("   --showtickets (configured in zendesk.view property)");
		System.out.println("   --showissues (configured in release.jira.issues)");		
		System.out.println("   --showreleasedtickets (configured in zendesk.view property and release.jira.issues)");		
	}


	private ZClient() throws Exception {
		config = loadConfiguration();
		jiraClient = new JiraClient(config.getProperty("jira.url"), config.getProperty("jira.user"), config.getProperty("jira.password"));
		zdClient = new ZendeskClient(config.getProperty("zendesk.domain"), config.getProperty("zendesk.username"), config.getProperty("zendesk.token"));
	}
	
	
	private Properties loadConfiguration() throws Exception {
		InputStream input = this.getClass().getClassLoader().getResourceAsStream(CONFIG_FILE);
    	if (input == null) {
    		throw new Exception("Cannot load file '" + CONFIG_FILE + "'");
    	}

        Properties properties = new Properties();
        properties.load(input);
        
        return properties;
	}
	
	
	private void release() throws Exception {
		String viewId = config.getProperty("zendesk.view");
		
		List<String> jiraKeys = Arrays.asList(config.getProperty("release.jira.issues").trim().split("[\\s]+"));
		Map<String, Ticket> ticketsWithIssue = getTicketsWithIssueFromView(viewId);		
		Map<String, Issue> jiraIssues = getJiraIssues(jiraKeys);
		
		jiraKeys.forEach(jiraKey -> {
			if (ticketsWithIssue.containsKey(jiraKey)) {
				releaseZendeskTicket(ticketsWithIssue.get(jiraKey), jiraIssues.get(jiraKey));
			}
			releaseJiraIssue(jiraKey, jiraIssues.get(jiraKey));
		});
	}


	private void releaseJiraIssue(String jiraKey, Issue issue) {
		logger.info("Jira.release: '{}'", issue.toShortString());
		try {
			String comment = createReleaseComment(issue);
			jiraClient.addIssueComment(issue, comment);
			jiraClient.releaseIssue(issue, comment);
			if (issue.getFields().getStatus().getId() == Status.RESOLVED) {
				jiraClient.transitionIssue(issue, Transition.CLOSE);
			}
		} catch (JiraException e) {
			logger.error(e.getMessage());
			logger.debug("[EXCEPTION]: ", e);
		}
	}


	private void releaseZendeskTicket(Ticket ticket, Issue issue) {
		logger.info("Zendesk.release: '{}'", ticket.toShortString());
		try {
			zdClient.releaseTicket(ticket, createReleaseComment(issue));
		} catch (ZendeskException e) {
			logger.error(e.getMessage());
			logger.debug("[EXCEPTION]: ", e);
		}
	}


	private Map<String, Ticket> getTicketsWithIssueFromView(String viewId) throws Exception {		
		Map<String, Ticket> ticketsWithIssue = zdClient.getTicketsWithIssueFromView(viewId);
		logger.debug("View '{}' has '{}' tickets.", viewId, ticketsWithIssue.entrySet().size());
		
		StringBuffer sbTickets = new StringBuffer();
		StringBuffer sbIssues = new StringBuffer();
		
		ticketsWithIssue.keySet().forEach(jiraKey -> {
			Ticket ticket = ticketsWithIssue.get(jiraKey);
			Issue issue = null;
			try {
				issue = jiraClient.getIssue(jiraKey);
			} catch (JiraException e) {}
						
			addFilteredTickets(ticket, jiraKey, issue, sbTickets, sbIssues);
		});
		
		logger.debug("zendesk: {}", sbTickets.toString());
		logger.debug("jira: {}", sbIssues.toString());
		
		return ticketsWithIssue;
	}
	
	
	// this method is mainly for testing.
	private void addFilteredTickets(Ticket ticket, String jiraKey, Issue issue, StringBuffer sbTickets, StringBuffer sbIssues) {
		if (issue == null) {
			logger.debug("   {}/{}: zd{} - jira{}", ticket.getId(), jiraKey, ticket, null);
			return;
		} 
		
		boolean isFiltered = issue.getFields() != null && issue.getFields().getStatus() != null && (
			issue.getFields().getStatus().getName().equalsIgnoreCase("zclosed") ||
			issue.getFields().getStatus().getName().equalsIgnoreCase("testing") ||
			issue.getFields().getStatus().getName().equalsIgnoreCase("zresolved"));
		
		isFiltered = true;
		
		if (isFiltered) {
			logger.debug("   {}/{}: zd{} - jira{}", ticket.getId(), issue.getKey(), ticket, issue);

			sbTickets.append(ticket.getId()).append(" ");
			sbIssues.append(jiraKey).append(" ");
		}		
	}


	private void showReleasedTickets() throws Exception {
		String viewId = config.getProperty("zendesk.view");
		
		List<String> jiraKeys = Arrays.asList(config.getProperty("release.jira.issues").trim().split("[\\s]+"));
		Map<String, Ticket> ticketsWithIssue = zdClient.getTicketsWithIssueFromView(viewId);
		logger.debug("View '{}' has '{}' tickets.", viewId, ticketsWithIssue.entrySet().size());
		
		
		jiraKeys.forEach(jiraKey -> {
			if (ticketsWithIssue.containsKey(jiraKey)) {
				Ticket ticket = ticketsWithIssue.get(jiraKey);
				Issue issue = null;
				try {
					issue = jiraClient.getIssue(jiraKey);
				} catch (JiraException e) {}

				logger.info("   {}/{}: zd{} - jira{}", ticket.getId(), jiraKey, ticket.toShortString(), (issue!=null) ? issue.toShortString() : "{}");
			}
		});
	}


	private Map<String, Issue> getJiraIssues(List<String> jiraKeys) {
		HashSet<String> keys = new HashSet<String>(jiraKeys);
		Map<String, Issue> issues = jiraClient.getIssues(keys);
		logger.debug("Found '{}' issues of '{}' keys.", issues.size(), keys.size());
		issues.values().forEach(t -> {
			logger.debug("   {}", t.toString());
		});
		
		return issues;
	}
	
	
	private String createReleaseComment(Issue issue) {
		final String NL = "\n";
		
		String comment = "### kbot begin. " + LocalDate.now().toString() + " " + LocalTime.now().toString() + NL;
		comment += config.getProperty("release.message") + NL;
		
		if (null != issue) {
			comment += "jira: " + issue.toShortString() + NL;
		}
		comment += "### kbot end." + NL;
		
		return comment;
	}
	
	
	private String getProperty(String key) {
		return config.getProperty(key);
	}


}
