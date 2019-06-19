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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import mcp.kiuwan.zendesk.beans.Comment;
import mcp.kiuwan.zendesk.beans.Field;
import mcp.kiuwan.zendesk.beans.Note;
import mcp.kiuwan.zendesk.beans.Ticket;
import mcp.kiuwan.zendesk.beans.TicketPage;

public class ZendeskClient {
	private final Long JIRA_FIELD_ID = 360001675219L;

	private HttpAuthenticationFeature feature;

	private ClientConfig clientConfig;
	private Client client;

	private String domain;
	
	public ZendeskClient(String domain, String username, String password) {
		this.domain = domain;
		
		feature = HttpAuthenticationFeature.basicBuilder().credentials(username+"/token", password).build();

		clientConfig = new ClientConfig();
		clientConfig.register(feature);
		
		client = ClientBuilder.newClient(clientConfig);
	}

	
	private WebTarget getWebTarget() {
		return client.target("https://" + domain + ".zendesk.com");
	}
	
	
	public List<Ticket> getTicketsFromView(String viewId) throws ZendeskException {
		WebTarget target = getWebTarget().path("/api/v2/views/" + viewId + "/tickets.json");
			
		Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON);

		Response response = builder.get();
		if (response.getStatus() != 200) {
			throw new ZendeskException(response);
		}
		
		TicketPage ticketPage = response.readEntity(TicketPage.class);
		response.close();
		
		return ticketPage.getTickets();
	}
	
		
	public Map<String, Ticket> getTicketsWithIssueFromView(String viewId) throws ZendeskException {
		Map<String, Ticket> ticketsWithIssue = new LinkedHashMap<>();
		
		List<Ticket> tickets = getTicketsFromView(viewId);
		tickets.forEach(ticket -> {
			List<Field> fields = ticket.getFields();
			fields.forEach(field -> {
				if (JIRA_FIELD_ID.equals(field.getId())) {
					String value = (String) field.getValue();
					if (null != value) {
						List<String> jiraKeys = Arrays.asList(value.trim().split("[\\s,]+"));
						jiraKeys.forEach(key -> {
							ticketsWithIssue.put(key, ticket);
						});
					}
				}
			});
		});
		
		return ticketsWithIssue;
	}


	public void releaseTicket(Ticket t, String msg) throws ZendeskException {
		WebTarget target = getWebTarget().path("/api/v2/tickets/" + t.getId() + ".json");
			
		Note note = new Note();
		Ticket ticket = new Ticket();	
		Comment comment = new Comment(msg);
		
		ticket.setComment(comment);
		ticket.releaseTicket();
		note.setTicket(ticket);
		
		Invocation.Builder builder = target.request();
		Response response = builder.put(Entity.json(note));
		if (response.getStatus() != 200) {
			throw new ZendeskException(response);
		}
		response.close();
	}

}
