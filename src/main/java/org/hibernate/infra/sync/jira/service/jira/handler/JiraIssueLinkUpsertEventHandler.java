package org.hibernate.infra.sync.jira.service.jira.handler;

import org.hibernate.infra.sync.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.sync.jira.service.jira.client.JiraRestException;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.sync.jira.service.jira.model.rest.JiraIssueLink;
import org.hibernate.infra.sync.jira.service.reporting.ReportingConfig;

public class JiraIssueLinkUpsertEventHandler extends JiraEventHandler {

	public JiraIssueLinkUpsertEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context, Long id) {
		super(reportingConfig, context, id);
	}

	@Override
	protected void doRun() {
		JiraIssueLink sourceLink = null;
		try {
			sourceLink = context.sourceJiraClient().getIssueLink(objectId);
		} catch (JiraRestException e) {
			failureCollector.critical("Source issue link %d was not found through the REST API".formatted(objectId), e);
			// no point in continuing anything
			return;
		}

		// make sure that both sides of the link exist:
		String outwardIssue = toDestinationKey(sourceLink.outwardIssue.key);
		String inwardIssue = toDestinationKey(sourceLink.inwardIssue.key);
		context.createNextPlaceholderBatch(outwardIssue);
		context.createNextPlaceholderBatch(inwardIssue);
		JiraIssue issue = context.destinationJiraClient().getIssue(inwardIssue);

		if (issue.fields.issuelinks != null) {
			// do we already have this issue link or not ?
			for (JiraIssueLink issuelink : issue.fields.issuelinks) {
				if ((outwardIssue.equals(issuelink.outwardIssue.key) || inwardIssue.equals(issuelink.inwardIssue.key))
						&& issuelink.type.name.equals(sourceLink.type.name)) {
					return;
				}
			}
		}

		JiraIssueLink toCreate = new JiraIssueLink();
		toCreate.type.id = linkType(sourceLink.type.id).orElse(null);
		toCreate.inwardIssue.key = inwardIssue;
		toCreate.outwardIssue.key = outwardIssue;
		context.destinationJiraClient().upsertIssueLink(toCreate);
	}
}
