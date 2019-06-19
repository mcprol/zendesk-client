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

package mcp.kiuwan.jira;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import mcp.kiuwan.jira.beans.Fields;
import mcp.kiuwan.jira.beans.Issue;

public class JiraClient {
	private final String ISSUE_PATH = "/rest/api/2/issue/";
	
	private static Logger logger = LoggerFactory.getLogger(JiraClient.class);
	
	private HttpAuthenticationFeature feature;

	private ClientConfig clientConfig;
	private Client client;

	private String uri;

	public JiraClient(String uri, String user, String password) {
		this.uri = uri;
		feature = HttpAuthenticationFeature.basicBuilder().credentials(user, password).build();

		clientConfig = new ClientConfig();
		clientConfig.register(feature);
		
		client = ClientBuilder.newClient(clientConfig);
	}

	
	public Issue getIssue(String key) throws JiraException {
		WebTarget webTarget = client.target(uri)
			.path(ISSUE_PATH + key)
			.queryParam("fields", "status", "resolution", "summary", "customfield_10321");
		
		Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);

		Response response = invocationBuilder.get();
		if (response.getStatus() != 200) {
			String errorMsg = response.readEntity(String.class);
			throw new JiraException(response, errorMsg);
		}

		return response.readEntity(Issue.class);
	}

	
	public Map<String, Issue> getIssues(Set<String> keys) {
		Map<String, Issue> issues = new HashMap<>();
		
		keys.forEach(k -> {
			try {
				Issue issue = getIssue(k);
				issues.put(k, issue);
			} catch (JiraException e) {
				logger.error(e.getMessage());
				logger.debug("[EXCEPTION]: ", e);
			}
		});
		
		return issues;
	}

	
	public void releaseIssue(Issue issue, String msg) throws JiraException {
		String releaseNotes = issue.getFields().getCustomfield_10321();
		String customfield_10321 = msg + "\n" + ((releaseNotes!=null) ? releaseNotes : "");
		
		Fields fields = new Fields();
		fields.setCustomfield_10321(customfield_10321);

		Issue updateIssue = new Issue();
		updateIssue.setFields(fields);

		WebTarget target = client.target(uri).path(ISSUE_PATH + issue.getKey());
			
		Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON);

		Response response = builder.put(Entity.json(updateIssue));
		if (response.getStatus() != 200 && response.getStatus() != 204) {
			String errorMsg = response.readEntity(String.class);
			throw new JiraException(response, errorMsg);
		}
		response.close();
	}

	
	public void addIssueComment(Issue issue, String commentMsg) throws JiraException {
		final JsonNodeFactory factory = JsonNodeFactory.instance;

		ObjectNode add =  factory.objectNode();
		add.put("body", commentMsg);
		
		ObjectNode comment =  factory.objectNode();
		comment.set("add", add);
		
		ArrayNode arrayOfComments = factory.arrayNode();
		arrayOfComments.add(comment);

		ObjectNode update =  factory.objectNode();
		update.set("comment", arrayOfComments);
	
		ObjectNode data =  factory.objectNode();
		data.set("update", update);

		updateIssue(issue, data);
	}
	

	public void transitionIssue(Issue issue, Long status) throws JiraException {
		final JsonNodeFactory factory = JsonNodeFactory.instance;

		ObjectNode transition =  factory.objectNode();
		transition.put("id", status.toString());
		
		ObjectNode data =  factory.objectNode();
		data.set("transition", transition);

		String path = ISSUE_PATH + issue.getKey() + "/transitions";
		post(path, data);
	}
	
	
	private void updateIssue(Issue issue, ObjectNode data) throws JiraException {
		String path = ISSUE_PATH + issue.getKey();
		put(path, data);
	}
	
	
	private void post(String path, ObjectNode data) throws JiraException {
		WebTarget target = client.target(uri).path(path);
		
		Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON);

		Response response = builder.post(Entity.json(data));
		if (response.getStatus() != 200 && response.getStatus() != 204) {
			String errorMsg = response.readEntity(String.class);
			throw new JiraException(response, errorMsg);
		}
		response.close();
	}
	
	
	private void put(String path, ObjectNode data) throws JiraException {
		WebTarget target = client.target(uri).path(path);
		
		Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON);

		Response response = builder.put(Entity.json(data));
		if (response.getStatus() != 200 && response.getStatus() != 204) {
			String errorMsg = response.readEntity(String.class);
			throw new JiraException(response, errorMsg);
		}
		response.close();
	}
}
