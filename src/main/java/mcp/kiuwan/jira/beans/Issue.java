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

package mcp.kiuwan.jira.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.Getter;
import lombok.Setter;
import mcp.kiuwan.zendesk.beans.Field;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Issue {
	private Long id;
	private String key;
	private Fields fields;
	
	
	@Override
	public String toString() {
		return objectAsString(this);
	}

	
	public String toShortString() {
		final JsonNodeFactory factory = JsonNodeFactory.instance;

		ObjectNode node =  factory.objectNode();
		node.put("key", getKey());
		node.put("status", fields.getStatus().getName());
		node.put("resolution", (fields.getResolution()!=null ? fields.getResolution().getName() : ""));
		node.put("releaseNotes", fields.getCustomfield_10321());
		
		return objectAsString(node);
	}
	
	
	private String objectAsString(Object object) {
		ObjectMapper objectMapper = new ObjectMapper();
		String valueAsString;
		try {
			valueAsString = objectMapper.writeValueAsString(object);
		} catch (JsonProcessingException e) {
			valueAsString = e.getMessage();
		}
		
		return valueAsString;
	}
	
}
