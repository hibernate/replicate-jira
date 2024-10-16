package org.hibernate.infra.replicate.jira.service.jira.handler;

import org.hibernate.infra.replicate.jira.service.jira.HandlerProjectContext;
import org.hibernate.infra.replicate.jira.service.jira.client.JiraRestException;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssue;
import org.hibernate.infra.replicate.jira.service.jira.model.rest.JiraIssueLink;
import org.hibernate.infra.replicate.jira.service.reporting.ReportingConfig;

public class JiraIssueLinkDeleteEventHandler extends JiraEventHandler {
	private final Long sourceIssueId;

	private final Long destinationIssueId;
	private final String issueLinkTypeId;
	public JiraIssueLinkDeleteEventHandler(ReportingConfig reportingConfig, HandlerProjectContext context, Long id,
			Long sourceIssueId, Long destinationIssueId, String issueLinkTypeId) {
		super(reportingConfig, context, id);
		this.sourceIssueId = sourceIssueId;
		this.destinationIssueId = destinationIssueId;
		this.issueLinkTypeId = issueLinkTypeId;
	}

	@Override
	protected void doRun() {
		JiraIssue sourceIssue = null;
		JiraIssue destinationIssue = null;
		try {
			sourceIssue = context.sourceJiraClient().getIssue(sourceIssueId);
			destinationIssue = context.sourceJiraClient().getIssue(destinationIssueId);
		} catch (JiraRestException e) {
			failureCollector.critical("Source/destination issues %d/%d were not found through the REST API"
					.formatted(sourceIssueId, destinationIssueId), e);
			// no point in continuing anything
			return;
		}

		if (sourceIssue.fields.issuelinks != null) {
			// check that there's really no such issue:
			for (JiraIssueLink issuelink : sourceIssue.fields.issuelinks) {
				if ((destinationIssue.key.equals(issuelink.outwardIssue.key)
						|| destinationIssue.key.equals(issuelink.inwardIssue.key))
						&& issuelink.type.id.equals(issueLinkTypeId)) {
					// we found one so we won't be deleting anything here !
					return;
				}
			}
		}

		// make sure that both sides of the link exist:
		String outwardIssue = toDestinationKey(sourceIssue.key);
		String inwardIssue = toDestinationKey(destinationIssue.key);
		// we will let it fail if one issue does not exist as that would mean that the
		// link is also not there:
		context.destinationJiraClient().getIssue(outwardIssue);
		JiraIssue issue = context.destinationJiraClient().getIssue(inwardIssue);

		String linkType = linkType(issueLinkTypeId).orElseThrow();
		if (issue.fields.issuelinks != null) {
			// do we already have this issue link or not ?
			for (JiraIssueLink issuelink : issue.fields.issuelinks) {
				if ((outwardIssue.equals(issuelink.inwardIssue.key) || inwardIssue.equals(issuelink.outwardIssue.key))
						&& issuelink.type.id.equals(linkType)) {
					context.destinationJiraClient().deleteIssueLink(issuelink.id);
				}
			}
		}
	}

	@Override
	public String toString() {
		return "JiraIssueLinkDeleteEventHandler[" + "sourceIssueId=" + sourceIssueId + ", destinationIssueId="
				+ destinationIssueId + ", issueLinkTypeId='" + issueLinkTypeId + '\'' + ", project="
				+ context.projectName() + ']';
	}
}
