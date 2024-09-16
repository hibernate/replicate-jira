package org.hibernate.infra.sync.jira.service.jira.model.rest;

import java.util.ArrayList;
import java.util.List;

public class JiraTextBody extends JiraTextContent {
	public Integer version = 1;
	public List<JiraTextContent> content = new ArrayList<>();

	public JiraTextBody() {
		this.type = "doc";
	}
}
