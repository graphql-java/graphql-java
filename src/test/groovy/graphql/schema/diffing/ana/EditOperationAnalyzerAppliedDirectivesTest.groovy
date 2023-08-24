package graphql.schema.diffing.ana

import graphql.TestUtil
import graphql.schema.diffing.SchemaDiffing
import spock.lang.Specification

import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveAddition
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveArgumentDeletion
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveArgumentRename
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveArgumentValueModification
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveDeletion
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveDirectiveArgumentLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveEnumLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveEnumValueLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveInputObjectFieldLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveInputObjectLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveInterfaceFieldArgumentLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveInterfaceFieldLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveInterfaceLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveObjectFieldArgumentLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveObjectFieldLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveObjectLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveScalarLocation
import static graphql.schema.diffing.ana.SchemaDifference.AppliedDirectiveUnionLocation
import static graphql.schema.diffing.ana.SchemaDifference.DirectiveModification
import static graphql.schema.diffing.ana.SchemaDifference.EnumModification
import static graphql.schema.diffing.ana.SchemaDifference.InputObjectModification
import static graphql.schema.diffing.ana.SchemaDifference.InterfaceModification
import static graphql.schema.diffing.ana.SchemaDifference.ObjectModification
import static graphql.schema.diffing.ana.SchemaDifference.ScalarModification
import static graphql.schema.diffing.ana.SchemaDifference.UnionModification

class EditOperationAnalyzerAppliedDirectivesTest extends Specification {

    def "reproduce the null mapping case"() {
        def commonSchema = '''
directive @value(val: String!) on ENUM_VALUE
scalar DateTime
directive @scopes(required: [Scope!]!, product: GrantCheckProduct!) repeatable on FIELD_DEFINITION | OBJECT | INTERFACE

enum GrantCheckProduct {
    JIRA
    JIRA_SERVICEDESK
    CONFLUENCE
    COMPASS

    """Don't check whether a user has been granted access to a specific site(cloudId)"""
    NO_GRANT_CHECKS
}

enum Scope {

    """identity"""
    MANAGE_ORG                          @value(val: "manage:org")
    MANAGE_ORG_PUBLIC_APIS              @value(val: "manage/org/public-api")
    AUTH_CONFLUENCE_USER                @value(val: "auth:confluence-user")
    MANAGE_DIRECTORY                    @value(val: "manage:directory")
    READ_ME                             @value(val: "read:me")
    READ_ACCOUNT                        @value(val: "read:account")
    VIEW_USERPROFILE                    @value(val: "view:userprofile")
    IDENTITY_ATLASSIAN_EXTERNAL         @value(val: "identity:atlassian-external")

    """compass"""
    COMPASS_ATLASSIAN_EXTERNAL          @value(val: "compass:atlassian-external")
    READ_COMPASS_COMPONENT              @value(val: "read:component:compass")
    WRITE_COMPASS_COMPONENT             @value(val: "write:component:compass")
    READ_COMPASS_SCORECARD              @value(val: "read:scorecard:compass")
    WRITE_COMPASS_SCORECARD             @value(val: "write:scorecard:compass")
    READ_COMPASS_EVENT                  @value(val: "read:event:compass")
    WRITE_COMPASS_EVENT                 @value(val: "write:event:compass")
    READ_COMPASS_METRIC                 @value(val: "read:metric:compass")
    WRITE_COMPASS_METRIC                @value(val: "write:metric:compass")

    """confluence"""
    CONFLUENCE_ATLASSIAN_EXTERNAL       @value(val: "confluence:atlassian-external")

    READ_CONFLUENCE_CONTENT_ANALYTICS   @value(val: "read:analytics.content:confluence")

    READ_CONFLUENCE_AUDIT_LOG           @value(val: "read:audit-log:confluence")
    WRITE_CONFLUENCE_AUDIT_LOG          @value(val: "write:audit-log:confluence")

    READ_CONFLUENCE_CONFIGURATION       @value(val: "read:configuration:confluence")
    WRITE_CONFLUENCE_CONFIGURATION      @value(val: "write:configuration:confluence")

    READ_CONFLUENCE_PAGE                @value(val: "read:page:confluence")
    WRITE_CONFLUENCE_PAGE               @value(val: "write:page:confluence")
    DELETE_CONFLUENCE_PAGE              @value(val: "delete:page:confluence")
    
    READ_CONFLUENCE_BLOGPOST            @value(val: "read:blogpost:confluence")
    WRITE_CONFLUENCE_BLOGPOST           @value(val: "write:blogpost:confluence")
    DELETE_CONFLUENCE_BLOGPOST          @value(val: "delete:blogpost:confluence")

    READ_CONFLUENCE_CUSTOM_CONTENT      @value(val: "read:custom-content:confluence")
    WRITE_CONFLUENCE_CUSTOM_CONTENT     @value(val: "write:custom-content:confluence")
    DELETE_CONFLUENCE_CUSTOM_CONTENT    @value(val: "delete:custom-content:confluence")
    
    READ_CONFLUENCE_ATTACHMENT          @value(val: "read:attachment:confluence")
    WRITE_CONFLUENCE_ATTACHMENT         @value(val: "write:attachment:confluence")
    DELETE_CONFLUENCE_ATTACHMENT        @value(val: "delete:attachment:confluence")

    READ_CONFLUENCE_COMMENT             @value(val: "read:comment:confluence")
    WRITE_CONFLUENCE_COMMENT            @value(val: "write:comment:confluence")
    DELETE_CONFLUENCE_COMMENT           @value(val: "delete:comment:confluence")

    READ_CONFLUENCE_TEMPLATE            @value(val: "read:template:confluence")
    WRITE_CONFLUENCE_TEMPLATE           @value(val: "write:template:confluence")

    READ_CONFLUENCE_LABEL               @value(val: "read:label:confluence")
    WRITE_CONFLUENCE_LABEL              @value(val: "write:label:confluence")

    READ_CONFLUENCE_CONTENT_PERMISSION  @value(val: "read:content.permission:confluence")
    
    READ_CONFLUENCE_CONTENT_PROPERTY    @value(val: "read:content.property:confluence")
    WRITE_CONFLUENCE_CONTENT_PROPERTY   @value(val: "write:content.property:confluence")
    
    READ_CONFLUENCE_CONTENT_RESTRICTION @value(val: "read:content.restriction:confluence")
    WRITE_CONFLUENCE_CONTENT_RESTRICTION @value(val: "write:content.restriction:confluence")

    READ_CONFLUENCE_CONTENT_METADATA    @value(val: "read:content.metadata:confluence")

    READ_CONFLUENCE_WATCHER             @value(val: "read:watcher:confluence")
    WRITE_CONFLUENCE_WATCHER            @value(val: "write:watcher:confluence")

    READ_CONFLUENCE_GROUP               @value(val: "read:group:confluence")
    WRITE_CONFLUENCE_GROUP              @value(val: "write:group:confluence")

    READ_CONFLUENCE_INLINE_TASK         @value(val: "read:inlinetask:confluence")
    WRITE_CONFLUENCE_INLINE_TASK        @value(val: "write:inlinetask:confluence")

    READ_CONFLUENCE_RELATION            @value(val: "read:relation:confluence")
    WRITE_CONFLUENCE_RELATION           @value(val: "write:relation:confluence")

    READ_CONFLUENCE_SPACE               @value(val: "read:space:confluence")
    WRITE_CONFLUENCE_SPACE              @value(val: "write:space:confluence")
    DELETE_CONFLUENCE_SPACE             @value(val: "delete:space:confluence")

    READ_CONFLUENCE_SPACE_PERMISSION    @value(val: "read:space.permission:confluence")
    WRITE_CONFLUENCE_SPACE_PERMISSION   @value(val: "write:space.permission:confluence")

    READ_CONFLUENCE_SPACE_PROPERTY      @value(val: "read:space.property:confluence")
    WRITE_CONFLUENCE_SPACE_PROPERTY     @value(val: "write:space.property:confluence")

    READ_CONFLUENCE_USER_PROPERTY      @value(val: "read:user.property:confluence")
    WRITE_CONFLUENCE_USER_PROPERTY     @value(val: "write:user.property:confluence")

    READ_CONFLUENCE_SPACE_SETTING       @value(val: "read:space.setting:confluence")
    WRITE_CONFLUENCE_SPACE_SETTING      @value(val: "write:space.setting:confluence")

    READ_CONFLUENCE_USER                @value(val: "read:user:confluence")

    """ecosystem"""
    MANAGE_APP                          @value(val: "manage:app")
    STORAGE_APP                         @value(val: "storage:app")

    """jira - non granular
    Please add a granular scope as well.
    """
    READ_JIRA_USER                      @value(val: "read:jira-user")
    READ_JIRA_WORK                      @value(val: "read:jira-work")
    WRITE_JIRA_WORK                     @value(val: "write:jira-work")
    MANAGE_JIRA_PROJECT                 @value(val: "manage:jira-project")
    MANAGE_JIRA_CONFIGURATION           @value(val: "manage:jira-configuration")
    JIRA_ATLASSIAN_EXTERNAL             @value(val: "jira:atlassian-external")
    MANAGE_JIRA_DATA_PROVIDER           @value(val: "manage:jira-data-provider")
    MANAGE_JIRA_WEBHOOK                 @value(val: "manage:jira-webhook")

    """jira - granular scopes.
    Each Jira Mutation and Query should have one or more of these in an `@scope` tag and one of the non-granular scopes above
    """
    APPLICATION_ROLE_READ               @value(val: "read:application-role:jira")
    AUDIT_LOG_READ                      @value(val: "read:audit-log:jira")
    ASYNC_TASK_DELETE                   @value(val: "delete:async-task:jira")
    ATTACHMENT_DELETE                   @value(val: "delete:attachment:jira")
    ATTACHMENT_READ                     @value(val: "read:attachment:jira")
    ATTACHMENT_WRITE                    @value(val: "write:attachment:jira")
    AVATAR_DELETE                       @value(val: "delete:avatar:jira")
    AVATAR_READ                         @value(val: "read:avatar:jira")
    AVATAR_WRITE                        @value(val: "write:avatar:jira")
    COMMENT_DELETE                      @value(val: "delete:comment:jira")
    COMMENT_PROPERTY_DELETE             @value(val: "delete:comment.property:jira")
    COMMENT_PROPERTY_READ               @value(val: "read:comment.property:jira")
    COMMENT_PROPERTY_WRITE              @value(val: "write:comment.property:jira")
    COMMENT_READ                        @value(val: "read:comment:jira")
    COMMENT_WRITE                       @value(val: "write:comment:jira")
    CUSTOM_FIELD_CONTEXTUAL_CONFIGURATION_READ          @value(val: "read:custom-field-contextual-configuration:jira")
    CUSTOM_FIELD_CONTEXTUAL_CONFIGURATION_WRITE         @value(val: "write:custom-field-contextual-configuration:jira")
    DASHBOARD_DELETE                    @value(val: "delete:dashboard:jira")
    DASHBOARD_PROPERTY_DELETE           @value(val: "delete:dashboard.property:jira")
    DASHBOARD_PROPERTY_READ             @value(val: "read:dashboard.property:jira")
    DASHBOARD_PROPERTY_WRITE            @value(val: "write:dashboard.property:jira")
    DASHBOARD_READ                      @value(val: "read:dashboard:jira")
    DASHBOARD_WRITE                     @value(val: "write:dashboard:jira")
    FIELD_CONFIGURATION_SCHEME_DELETE   @value(val: "delete:field-configuration-scheme:jira")
    FIELD_CONFIGURATION_SCHEME_READ     @value(val: "read:field-configuration-scheme:jira")
    FIELD_CONFIGURATION_SCHEME_WRITE    @value(val: "write:field-configuration-scheme:jira")
    FIELD_CONFIGURATION_DELETE          @value(val: "delete:field-configuration:jira")
    FIELD_CONFIGURATION_READ            @value(val: "read:field-configuration:jira")
    FIELD_CONFIGURATION_WRITE           @value(val: "write:field-configuration:jira")
    FIELD_DEFAULT_VALUE_READ            @value(val: "read:field.default-value:jira")
    FIELD_DEFAULT_VALUE_WRITE           @value(val: "write:field.default-value:jira")
    FIELD_DELETE                        @value(val: "delete:field:jira")
    FIELD_OPTIONS_READ                  @value(val: "read:field.options:jira")
    FIELD_OPTION_DELETE                 @value(val: "delete:field.option:jira")
    FIELD_OPTION_READ                   @value(val: "read:field.option:jira")
    FIELD_OPTION_WRITE                  @value(val: "write:field.option:jira")
    FIELD_READ                          @value(val: "read:field:jira")
    FIELD_WRITE                         @value(val: "write:field:jira")
    FILTER_COLUMN_DELETE                @value(val: "delete:filter.column:jira")
    FILTER_COLUMN_READ                  @value(val: "read:filter.column:jira")
    FILTER_COLUMN_WRITE                 @value(val: "write:filter.column:jira")
    FILTER_DEFAULT_SHARE_SCOPE_READ     @value(val: "read:filter.default-share-scope:jira")
    FILTER_DEFAULT_SHARE_SCOPE_WRITE    @value(val: "write:filter.default-share-scope:jira")
    FILTER_DELETE                       @value(val: "delete:filter:jira")
    FILTER_READ                         @value(val: "read:filter:jira")
    FILTER_WRITE                        @value(val: "write:filter:jira")
    GROUP_DELETE                        @value(val: "delete:group:jira")
    GROUP_READ                          @value(val: "read:group:jira")
    GROUP_WRITE                         @value(val: "write:group:jira")
    INSTANCE_CONFIGURATION_READ         @value(val: "read:instance-configuration:jira")
    INSTANCE_CONFIGURATION_WRITE        @value(val: "write:instance-configuration:jira")
    ISSUE_ADJUSTMENTS_READ              @value(val: "read:issue-adjustments:jira")
    ISSUE_ADJUSTMENTS_WRITE             @value(val: "write:issue-adjustments:jira")
    ISSUE_ADJUSTMENTS_DELETE            @value(val: "delete:issue-adjustments:jira")
    ISSUE_EVENT_READ                    @value(val: "read:issue-event:jira")
    ISSUE_FIELD_VALUES_READ             @value(val: "read:issue-field-values:jira")
    ISSUE_CHANGELOG_READ                @value(val: "read:issue.changelog:jira")
    ISSUE_DELETE                        @value(val: "delete:issue:jira")
    ISSUE_DETAILS_READ                  @value(val: "read:issue-details:jira")
    ISSUE_LINK_DELETE                   @value(val: "delete:issue-link:jira")
    ISSUE_LINK_TYPE_DELETE              @value(val: "delete:issue-link-type:jira")
    ISSUE_LINK_TYPE_READ                @value(val: "read:issue-link-type:jira")
    ISSUE_LINK_TYPE_WRITE               @value(val: "write:issue-link-type:jira")
    ISSUE_LINK_READ                     @value(val: "read:issue-link:jira")
    ISSUE_LINK_WRITE                    @value(val: "write:issue-link:jira")
    ISSUE_PROPERTY_DELETE               @value(val: "delete:issue.property:jira")
    ISSUE_PROPERTY_READ                 @value(val: "read:issue.property:jira")
    ISSUE_PROPERTY_WRITE                @value(val: "write:issue.property:jira")
    ISSUE_READ                          @value(val: "read:issue:jira")
    ISSUE_REMOTE_LINK_DELETE            @value(val: "delete:issue.remote-link:jira")
    ISSUE_REMOTE_LINK_READ              @value(val: "read:issue.remote-link:jira")
    ISSUE_REMOTE_LINK_WRITE             @value(val: "write:issue.remote-link:jira")
    ISSUE_SECURITY_LEVEL_READ           @value(val: "read:issue-security-level:jira")
    ISSUE_SECURITY_SCHEME_READ          @value(val: "read:issue-security-scheme:jira")
    ISSUE_STATUS_READ                   @value(val: "read:issue-status:jira")
    ISSUE_TIME_TRACKING_READ            @value(val: "read:issue.time-tracking:jira")
    ISSUE_TIME_TRACKING_WRITE           @value(val: "write:issue.time-tracking:jira")
    ISSUE_TRANSITION_READ               @value(val: "read:issue.transition:jira")
    ISSUE_TYPE_DELETE                   @value(val: "delete:issue-type:jira")
    ISSUE_TYPE_HIERARCHY_READ           @value(val: "read:issue-type-hierarchy:jira")
    ISSUE_TYPE_PROPERTY_DELETE          @value(val: "delete:issue-type.property:jira")
    ISSUE_TYPE_PROPERTY_READ            @value(val: "read:issue-type.property:jira")
    ISSUE_TYPE_PROPERTY_WRITE           @value(val: "write:issue-type.property:jira")
    ISSUE_TYPE_READ                     @value(val: "read:issue-type:jira")
    ISSUE_TYPE_SCHEME_DELETE            @value(val: "delete:issue-type-scheme:jira")
    ISSUE_TYPE_SCHEME_READ              @value(val: "read:issue-type-scheme:jira")
    ISSUE_TYPE_SCHEME_WRITE             @value(val: "write:issue-type-scheme:jira")
    ISSUE_TYPE_SCREEN_SCHEME_DELETE     @value(val: "delete:issue-type-screen-scheme:jira")
    ISSUE_TYPE_SCREEN_SCHEME_READ       @value(val: "read:issue-type-screen-scheme:jira")
    ISSUE_TYPE_SCREEN_SCHEME_WRITE      @value(val: "write:issue-type-screen-scheme:jira")
    ISSUE_TYPE_WRITE                    @value(val: "write:issue-type:jira")
    ISSUE_VOTES_READ                    @value(val: "read:issue.votes:jira")
    ISSUE_VOTE_READ                     @value(val: "read:issue.vote:jira")
    ISSUE_VOTE_WRITE                    @value(val: "write:issue.vote:jira")
    ISSUE_WATCHER_READ                  @value(val: "read:issue.watcher:jira")
    ISSUE_WATCHER_WRITE                 @value(val: "write:issue.watcher:jira")
    ISSUE_WORKLOG_DELETE                @value(val: "delete:issue-worklog:jira")
    ISSUE_WORKLOG_PROPERTY_DELETE       @value(val: "delete:issue-worklog.property:jira")
    ISSUE_WORKLOG_PROPERTY_READ         @value(val: "read:issue-worklog.property:jira")
    ISSUE_WORKLOG_PROPERTY_WRITE        @value(val: "write:issue-worklog.property:jira")
    ISSUE_WORKLOG_READ                  @value(val: "read:issue-worklog:jira")
    ISSUE_WORKLOG_WRITE                 @value(val: "write:issue-worklog:jira")
    ISSUE_WRITE                         @value(val: "write:issue:jira")
    ISSUE_META_READ                     @value(val: "read:issue-meta:jira")
    JQL_READ                            @value(val: "read:jql:jira")
    JQL_VALIDATE                        @value(val: "validate:jql:jira")
    LABEL_READ                          @value(val: "read:label:jira")
    LICENSE_READ                        @value(val: "read:license:jira")
    NOTIFICATION_SCHEME_READ            @value(val: "read:notification-scheme:jira")
    NOTIFICATION_SEND                   @value(val: "send:notification:jira")
    PERMISSION_DELETE                   @value(val: "delete:permission:jira")
    PERMISSION_READ                     @value(val: "read:permission:jira")
    PERMISSION_SCHEME_DELETE            @value(val: "delete:permission-scheme:jira")
    PERMISSION_SCHEME_READ              @value(val: "read:permission-scheme:jira")
    PERMISSION_SCHEME_WRITE             @value(val: "write:permission-scheme:jira")
    PERMISSION_WRITE                    @value(val: "write:permission:jira")
    PRIORITY_READ                       @value(val: "read:priority:jira")
    PROJECT_AVATAR_DELETE               @value(val: "delete:project.avatar:jira")
    PROJECT_AVATAR_READ                 @value(val: "read:project.avatar:jira")
    PROJECT_AVATAR_WRITE                @value(val: "write:project.avatar:jira")
    PROJECT_CATEGORY_DELETE             @value(val: "delete:project-category:jira")
    PROJECT_CATEGORY_READ               @value(val: "read:project-category:jira")
    PROJECT_CATEGORY_WRITE              @value(val: "write:project-category:jira")
    PROJECT_COMPONENT_DELETE            @value(val: "delete:project.component:jira")
    PROJECT_COMPONENT_READ              @value(val: "read:project.component:jira")
    PROJECT_COMPONENT_WRITE             @value(val: "write:project.component:jira")
    PROJECT_EMAIL_READ                  @value(val: "read:project.email:jira")
    PROJECT_EMAIL_WRITE                 @value(val: "write:project.email:jira")
    PROJECT_FEATURE_READ                @value(val: "read:project.feature:jira")
    PROJECT_FEATURE_WRITE               @value(val: "write:project.feature:jira")
    PROJECT_PROPERTY_DELETE             @value(val: "delete:project.property:jira")
    PROJECT_PROPERTY_READ               @value(val: "read:project.property:jira")
    PROJECT_PROPERTY_WRITE              @value(val: "write:project.property:jira")
    PROJECT_ROLE_DELETE                 @value(val: "delete:project-role:jira")
    PROJECT_ROLE_READ                   @value(val: "read:project-role:jira")
    PROJECT_ROLE_WRITE                  @value(val: "write:project-role:jira")
    PROJECT_TYPE_READ                   @value(val: "read:project-type:jira")
    PROJECT_VERSION_DELETE              @value(val: "delete:project-version:jira")
    PROJECT_VERSION_READ                @value(val: "read:project-version:jira")
    PROJECT_VERSION_WRITE               @value(val: "write:project-version:jira")
    PROJECT_DELETE                      @value(val: "delete:project:jira")
    PROJECT_READ                        @value(val: "read:project:jira")
    PROJECT_WRITE                       @value(val: "write:project:jira")
    RESOLUTION_READ                     @value(val: "read:resolution:jira")
    SCREENABLE_FIELD_DELETE             @value(val: "delete:screenable-field:jira")
    SCREENABLE_FIELD_READ               @value(val: "read:screenable-field:jira")
    SCREENABLE_FIELD_WRITE              @value(val: "write:screenable-field:jira")
    SCREEN_DELETE                       @value(val: "delete:screen:jira")
    SCREEN_FIELD_READ                   @value(val: "read:screen-field:jira")
    SCREEN_READ                         @value(val: "read:screen:jira")
    SCREEN_SCHEME_DELETE                @value(val: "delete:screen-scheme:jira")
    SCREEN_SCHEME_READ                  @value(val: "read:screen-scheme:jira")
    SCREEN_SCHEME_WRITE                 @value(val: "write:screen-scheme:jira")
    SCREEN_TAB_DELETE                   @value(val: "delete:screen-tab:jira")
    SCREEN_TAB_READ                     @value(val: "read:screen-tab:jira")
    SCREEN_TAB_WRITE                    @value(val: "write:screen-tab:jira")
    SCREEN_WRITE                        @value(val: "write:screen:jira")
    STATUS_READ                         @value(val: "read:status:jira")
    USER_COLUMNS_READ                   @value(val: "read:user.columns:jira")
    USER_CONFIGURATION_DELETE           @value(val: "delete:user-configuration:jira")
    USER_CONFIGURATION_READ             @value(val: "read:user-configuration:jira")
    USER_CONFIGURATION_WRITE            @value(val: "write:user-configuration:jira")
    USER_PROPERTY_DELETE                @value(val: "delete:user.property:jira")
    USER_PROPERTY_READ                  @value(val: "read:user.property:jira")
    USER_PROPERTY_WRITE                 @value(val: "write:user.property:jira")
    USER_READ                           @value(val: "read:user:jira")
    WEBHOOK_READ                        @value(val: "read:webhook:jira")
    WEBHOOK_WRITE                       @value(val: "write:webhook:jira")
    WEBHOOK_DELETE                      @value(val: "delete:webhook:jira")
    WORKFLOW_DELETE                     @value(val: "delete:workflow:jira")
    WORKFLOW_PROPERTY_DELETE            @value(val: "delete:workflow.property:jira")
    WORKFLOW_PROPERTY_READ              @value(val: "read:workflow.property:jira")
    WORKFLOW_PROPERTY_WRITE             @value(val: "write:workflow.property:jira")
    WORKFLOW_READ                       @value(val: "read:workflow:jira")
    WORKFLOW_SCHEME_DELETE              @value(val: "delete:workflow-scheme:jira")
    WORKFLOW_SCHEME_READ                @value(val: "read:workflow-scheme:jira")
    WORKFLOW_SCHEME_WRITE               @value(val: "write:workflow-scheme:jira")
    WORKFLOW_WRITE                      @value(val: "write:workflow:jira")
    JIRA_EXPRESSIONS_READ               @value(val: "read:jira-expressions:jira")

    """jira-servicedesk - non-granular
    Please add a granular scope as well.
    """
    READ_SERVICEDESK_REQUEST            @value(val: "read:servicedesk-request")
    WRITE_SERVICEDESK_REQUEST           @value(val: "write:servicedesk-request")
    MANAGE_SERVICEDESK_CUSTOMER         @value(val: "manage:servicedesk-customer")

    """jira-servicedesk - granular
    Each JSM Mutation and Query should have one or more of these in an `@scope` tag and one of the non-granular scopes above.
    You can mix them with Jira scopes if needed.
    """
    READ_CUSTOMER                       @value(val: "read:customer:jira-service-management")
    WRITE_CUSTOMER                      @value(val: "write:customer:jira-service-management")
    READ_ORGANIZATION                   @value(val: "read:organization:jira-service-management")
    WRITE_ORGANIZATION                  @value(val: "write:organization:jira-service-management")
    DELETE_ORGANIZATION                 @value(val: "delete:organization:jira-service-management")
    READ_ORGANIZATION_USER              @value(val: "read:organization.user:jira-service-management")
    WRITE_ORGANIZATION_USER             @value(val: "write:organization.user:jira-service-management")
    DELETE_ORGANIZATION_USER            @value(val: "delete:organization.user:jira-service-management")
    READ_ORGANIZATION_PROPERTY          @value(val: "read:organization.property:jira-service-management")
    WRITE_ORGANIZATION_PROPERTY         @value(val: "write:organization.property:jira-service-management")
    DELETE_ORGANIZATION_PROPERTY        @value(val: "delete:organization.property:jira-service-management")
    READ_SERVICEDESK                    @value(val: "read:servicedesk:jira-service-management")
    WRITE_SERVICEDESK                   @value(val: "write:servicedesk:jira-service-management")
    READ_SERVICEDESK_ORGANIZATION       @value(val: "read:servicedesk.organization:jira-service-management")
    WRITE_SERVICEDESK_ORGANIZATION      @value(val: "write:servicedesk.organization:jira-service-management")
    DELETE_SERVICEDESK_ORGANIZATION     @value(val: "delete:servicedesk.organization:jira-service-management")
    READ_SERVICEDESK_CUSTOMER           @value(val: "read:servicedesk.customer:jira-service-management")
    WRITE_SERVICEDESK_CUSTOMER          @value(val: "write:servicedesk.customer:jira-service-management")
    DELETE_SERVICEDESK_CUSTOMER         @value(val: "delete:servicedesk.customer:jira-service-management")
    READ_SERVICEDESK_PROPERTY           @value(val: "read:servicedesk.property:jira-service-management")
    WRITE_SERVICEDESK_PROPERTY          @value(val: "write:servicedesk.property:jira-service-management")
    DELETE_SERVICEDESK_PROPERTY         @value(val: "delete:servicedesk.property:jira-service-management")
    READ_REQUESTTYPE                    @value(val: "read:requesttype:jira-service-management")
    WRITE_REQUESTTYPE                   @value(val: "write:requesttype:jira-service-management")
    READ_REQUESTTYPE_PROPERTY           @value(val: "read:requesttype.property:jira-service-management")
    WRITE_REQUESTTYPE_PROPERTY          @value(val: "write:requesttype.property:jira-service-management")
    DELETE_REQUESTTYPE_PROPERTY         @value(val: "delete:requesttype.property:jira-service-management")
    READ_QUEUE                          @value(val: "read:queue:jira-service-management")
    READ_REQUEST                        @value(val: "read:request:jira-service-management")
    WRITE_REQUEST                       @value(val: "write:request:jira-service-management")
    READ_REQUEST_APPROVAL               @value(val: "read:request.approval:jira-service-management")
    WRITE_REQUEST_APPROVAL              @value(val: "write:request.approval:jira-service-management")
    READ_REQUEST_PARTICIPANT            @value(val: "read:request.participant:jira-service-management")
    WRITE_REQUEST_PARTICIPANT           @value(val: "write:request.participant:jira-service-management")
    DELETE_REQUEST_PARTICIPANT          @value(val: "delete:request.participant:jira-service-management")
    READ_REQUEST_ACTION                 @value(val: "read:request.action:jira-service-management")
    READ_REQUEST_COMMENT                @value(val: "read:request.comment:jira-service-management")
    WRITE_REQUEST_COMMENT               @value(val: "write:request.comment:jira-service-management")
    READ_REQUEST_SLA                    @value(val: "read:request.sla:jira-service-management")
    READ_REQUEST_ATTACHMENT             @value(val: "read:request.attachment:jira-service-management")
    WRITE_REQUEST_ATTACHMENT            @value(val: "write:request.attachment:jira-service-management")
    READ_REQUEST_STATUS                 @value(val: "read:request.status:jira-service-management")
    WRITE_REQUEST_STATUS                @value(val: "write:request.status:jira-service-management")
    READ_REQUEST_FEEDBACK               @value(val: "read:request.feedback:jira-service-management")
    WRITE_REQUEST_FEEDBACK              @value(val: "write:request.feedback:jira-service-management")
    DELETE_REQUEST_FEEDBACK             @value(val: "delete:request.feedback:jira-service-management")
    READ_REQUEST_NOTIFICATION           @value(val: "read:request.notification:jira-service-management")
    WRITE_REQUEST_NOTIFICATION          @value(val: "write:request.notification:jira-service-management")
    DELETE_REQUEST_NOTIFICATION         @value(val: "delete:request.notification:jira-service-management")
    READ_KNOWLEDGEBASE                  @value(val: "read:knowledgebase:jira-service-management")

    """jsw scopes
    Note - JSW does not have non granular scopes so it does not need two scope tags like JSM/Jira
    """
    READ_JSW_BOARD_SCOPE                @value(val: "read:board-scope:jira-software")
    WRITE_JSW_BOARD_SCOPE               @value(val: "write:board-scope:jira-software")
    READ_JSW_BOARD_SCOPE_ADMIN          @value(val: "read:board-scope.admin:jira-software")
    WRITE_JSW_BOARD_SCOPE_ADMIN         @value(val: "write:board-scope.admin:jira-software")
    DELETE_JSW_BOARD_SCOPE_ADMIN        @value(val: "delete:board-scope.admin:jira-software")
    READ_JSW_EPIC                       @value(val: "read:epic:jira-software")
    WRITE_JSW_EPIC                      @value(val: "write:epic:jira-software")
    READ_JSW_ISSUE                      @value(val: "read:issue:jira-software")
    WRITE_JSW_ISSUE                     @value(val: "write:issue:jira-software")
    READ_JSW_SPRINT                     @value(val: "read:sprint:jira-software")
    WRITE_JSW_SPRINT                    @value(val: "write:sprint:jira-software")
    DELETE_JSW_SPRINT                   @value(val: "delete:sprint:jira-software")
    READ_JSW_SOURCE_CODE                @value(val: "read:source-code:jira-software")
    WRITE_JSW_SOURCE_CODE               @value(val: "write:source-code:jira-software")
    READ_JSW_FEATURE_FLAG               @value(val: "read:feature-flag:jira-software")
    WRITE_JSW_FEATURE_FLAG              @value(val: "write:feature-flag:jira-software")
    READ_JSW_DEPLOYMENT                 @value(val: "read:deployment:jira-software")
    WRITE_JSW_DEPLOYMENT                @value(val: "write:deployment:jira-software")
    READ_JSW_BUILD                      @value(val: "read:build:jira-software")
    WRITE_JSW_BUILD                     @value(val: "write:build:jira-software")
    READ_JSW_REMOTE_LINK                @value(val: "read:remote-link:jira-software")
    WRITE_JSW_REMOTE_LINK               @value(val: "write:remote-link:jira-software")

    """notification-log"""
    READ_NOTIFICATIONS                  @value(val: "read:notifications")
    WRITE_NOTIFICATIONS                 @value(val: "write:notifications")

    """outbound-auth"""
    ADMIN_CONTAINER                     @value(val: "admin:container")
    READ_CONTAINER                      @value(val: "read:container")
    WRITE_CONTAINER                     @value(val: "write:container")

    """platform"""
    MIGRATE_CONFLUENCE                  @value(val: "migrate:confluence")

    """teams"""
    READ_TEAM                           @value(val: "view:team:teams")
    READ_TEAM_MEMBERS                   @value(val: "view:membership:teams")

    """trello"""
    TRELLO_ATLASSIAN_EXTERNAL           @value(val: "trello:atlassian-external")

    """papi"""
    CATALOG_READ                       @value(val: "read:catalog:all")
    API_ACCESS                         @value(val: "api_access")
}
'''
        given:
        // this has come from the current master branch
        def oldSdl = commonSchema + '''
type Query {
    """
    Query object for Notification Experience
    """
    notifications: InfluentsNotificationQuery
    # Scopes are OR'd i.e acquiring any one of these scopes will allow the consumer consume activity data
    # For backwards compatibility with v2
    @scopes(required: [CONFLUENCE_ATLASSIAN_EXTERNAL], product: NO_GRANT_CHECKS)
    @scopes(required: [JIRA_ATLASSIAN_EXTERNAL], product: NO_GRANT_CHECKS)
    # Granular scopes so consumers are not forced to acquire the above "wider" ones
    @scopes(required: [READ_JIRA_WORK], product: NO_GRANT_CHECKS)
    @scopes(required: [READ_CONFLUENCE_BLOGPOST], product: NO_GRANT_CHECKS)
    @scopes(required: [READ_CONFLUENCE_COMMENT], product: NO_GRANT_CHECKS)
    @scopes(required: [READ_CONFLUENCE_PAGE], product: NO_GRANT_CHECKS)
    @scopes(required: [READ_CONFLUENCE_SPACE], product: NO_GRANT_CHECKS)
}

type InfluentsNotificationQuery {
    """
    API for fetching user's notifications.
    """
    notificationFeed(
        feedFilter: InfluentsNotificationFeedFilter
        first: Int = 25
        after: String
    ): InfluentsNotificationGroupedConnection!

    """
    API for fetching user's un-read direct notification count.
    """
    unseenNotificationCount(workspaceId: String, product: String): Int!
}

input InfluentsNotificationFeedFilter {
    workspaceId: String
    categoryFilter: InfluentsNotificationCategory
    readStateFilter: InfluentsNotificationReadState
    productFilter: String
    groupId: String
}

enum InfluentsNotificationCategory {
    direct
    watching
}

enum InfluentsNotificationReadState {
    unread
    read
}

type InfluentsNotificationPageInfo {
    hasNextPage: Boolean!
    hasPreviousPage: Boolean!
    startCursor: String
    endCursor: String
}

type InfluentsNotificationAnalyticsAttribute {
    key: String
    value: String
}

type InfluentsNotificationActor {
    displayName: String
    ari: String
    avatarURL: String
}

type InfluentsNotificationDocument {
    format: String
    data: String
}

"""
A body item can be sent with two types of appearances, PRIMARY and QUOTED.
The latter can be used to for sending comment reply style notifications.
"""
type InfluentsNotificationBodyItem {
    type: String
    appearance: String
    document: InfluentsNotificationDocument
    author: InfluentsNotificationActor
}

enum InfluentsNotificationAppearance {
    DEFAULT
    PRIMARY
    LINK
    SUBTLE
    WARNING
    DANGER
}

"""
Call-to-action link where the notification is directed to.
"""
type InfluentsNotificationAction {
    title: String!
    url: String
    appearance: InfluentsNotificationAppearance!
}

"""
A path provides context for the entity.
This is particularly important if this is the first time the recipient has been made aware of the resource, or if multiple entities use the same or similar titles. The contents of the path are user defined, you may choose to end with the entity or not to.
"""
type InfluentsNotificationPath {
    title: String
    url: String
    iconUrl: String
}

"""
The Entity is what the notification relates to –
in most cases it’s the object (page, issue, pull request) that has been interacted with.
Clicking the title takes the user to the entity.
An entity can have a related icon.
"""
type InfluentsNotificationEntity {
    title: String
    iconUrl: String
    url: String
}

type InfluentsNotificationTemplateVariable {
    name: String!
    type: String!
    id: ID!
    fallback: String!
}

type InfluentsNotificationContent {
    type: String!
    message: String!
    url: String
    actor: InfluentsNotificationActor!
    entity: InfluentsNotificationEntity
    path: [InfluentsNotificationPath!]
    actions: [InfluentsNotificationAction!]
    bodyItems: [InfluentsNotificationBodyItem!]
    templateVariables: [InfluentsNotificationTemplateVariable!]
}

"""
A single user notification item.
"""
type InfluentsNotificationItem {
    """
    Unique identity of the notification
    """
    notificationId: ID!
    timestamp: DateTime!
    content: InfluentsNotificationContent!
    readState: InfluentsNotificationReadState!
    category: InfluentsNotificationCategory!
    workspaceId: String
    analyticsAttributes: [InfluentsNotificationAnalyticsAttribute!]
}

"""
A grouped notification item containing the head notification item from each group
along with the count of items grouped/collapsed.
"""
type InfluentsNotificationGroupedItem {
    groupId: ID!
    groupSize: Int!
    headNotification: InfluentsNotificationItem!
    """
    childItems represents notification items(other than head notification) that belong to a group
    """
    childItems(first: Int, after: String): [InfluentsNotificationItem!]
}

"""
Notification Feed with pagination cursor
"""
type InfluentsNotificationGroupedConnection {
    nodes: [InfluentsNotificationGroupedItem!]!
    pageInfo: InfluentsNotificationPageInfo!
}

# --------------------------------------- notifications_mutation_api
type Mutation {
    """
    Mutation object for Notification Experience
    """
    notifications: InfluentsNotificationMutation
    # Scopes are OR'd i.e acquiring any one of these scopes will allow the consumer consume activity data
    # For backwards compatibility with v2
    @scopes(required: [CONFLUENCE_ATLASSIAN_EXTERNAL], product: NO_GRANT_CHECKS)
    @scopes(required: [JIRA_ATLASSIAN_EXTERNAL], product: NO_GRANT_CHECKS)
    # Granular scopes so consumers are not forced to acquire the above "wider" ones
    @scopes(required: [READ_JIRA_WORK], product: NO_GRANT_CHECKS)
    @scopes(required: [READ_CONFLUENCE_BLOGPOST], product: NO_GRANT_CHECKS)
    @scopes(required: [READ_CONFLUENCE_COMMENT], product: NO_GRANT_CHECKS)
    @scopes(required: [READ_CONFLUENCE_PAGE], product: NO_GRANT_CHECKS)
    @scopes(required: [READ_CONFLUENCE_SPACE], product: NO_GRANT_CHECKS)
}

type InfluentsNotificationMutation {
    """
    API for marking the state of notifications(that belong to a particular product and category) as Read.

    With this endpoint clients can implement 'markAllAsRead' functionality.
    Only one before query parameter (beforeInclusive or beforeInclusiveTimestamp) .
    """
    markNotificationsAsRead(
        category: InfluentsNotificationCategory
        """
        Which product the notifications should be from. If omitted, the results are from any product.
        """
        product: String

        """
        Notifications will only be marked from the workspace with the specified workspace id.
        """
        workspaceId: String

        """
        The notificationId of the notification to mark and all older ones before the specified notification (INCLUSIVE).
        """
        beforeInclusive: String

        """
        Mark all notifications older than this timestamp in ISO-8601 format (INCLUSIVE).
        Format: date-time
        """
        beforeInclusiveTimestamp: String

    ): String

    """
    API for marking the state of notifications(that belong to a particular product and category) as unread.

    With this endpoint clients can implement 'markAllAsUnRead' functionality.
    Only one before query parameter (beforeInclusive or beforeInclusiveTimestamp) .
    """
    markNotificationsAsUnread(
        category: InfluentsNotificationCategory
        """
        Which product the notifications should be from. If omitted, the results are from any product.
        """
        product: String

        """
        Notifications will only be marked from the workspace with the specified workspace id.
        """
        workspaceId: String

        """
        The notificationId of the notification to mark and all older ones before the specified notification (INCLUSIVE).
        """
        beforeInclusive: String

        """
        Mark all notifications older than this timestamp in ISO-8601 format (INCLUSIVE).
        Format: date-time
        """
        beforeInclusiveTimestamp: String

    ): String


    """
    API for marking grouped notifications as read.
    With this endpoint clients can implement 'markAllAsRead' functionality for grouped notifications.
    """
    markNotificationsByGroupIdAsRead(
        category: InfluentsNotificationCategory
        """
        groupId for marking all notifications belonging to a group as read.
        """
        groupId: String

        """
        The notificationId of the notification to mark and all older ones (INCLUSIVE).
        """
        beforeInclusive: String

    ): String


    """
    API for marking grouped notifications as unread.
    With this endpoint clients can implement 'markAllAsUnRead' functionality for grouped notifications.
    """
    markNotificationsByGroupIdAsUnread(
        category: InfluentsNotificationCategory
        """
        groupId for marking grouped notifications  as unread.
        """
        groupId: String

        """
        The notificationId of the notification to mark and all older ones (INCLUSIVE).
        """
        beforeInclusive: String

    ): String

    """
    API for marking the notifications specified by ids as read.
    """
    markNotificationsByIdsAsRead(
        """
        The list of notifications specified by ids in which the state should be changed.
        Min items: 1
        Max items: 100
        Unique items: true
        """
        ids: [String!]!
    ): String

    """
    API for marking the state of notifications specified by ids as unread
    """
    markNotificationsByIdsAsUnread(
        """
        The list of notifications specified by ids in which the state should be changed.
        Min items: 1
        Max items: 100
        Unique items: true
        """
        ids: [String!]!
    ): String

    """
    API for archiving the notifications specified by ids.
    Note: Notifications will be removed from the datastore.
    """
    archiveNotifications(
        """
        The list of notifications specified by ids.
        Min items: 1
        Max items: 100
        Unique items: true
        """
        ids: [String!]!
    ): String

}
        '''

        // This has come from the last commit on the PR
        def newSdl = commonSchema + '''
type Query {
    """
    Query object for Notification Experience
    """
    notifications: InfluentsNotificationQuery
    # Scopes are OR'd i.e acquiring any one of these scopes will allow the consumer consume activity data
    # For backwards compatibility with v2
    @scopes(required: [CONFLUENCE_ATLASSIAN_EXTERNAL], product: NO_GRANT_CHECKS)
    @scopes(required: [JIRA_ATLASSIAN_EXTERNAL], product: NO_GRANT_CHECKS)
    # Granular scopes so consumers are not forced to acquire the above "wider" ones
    @scopes(required: [READ_JIRA_WORK], product: NO_GRANT_CHECKS)
    @scopes(required: [READ_CONFLUENCE_BLOGPOST], product: NO_GRANT_CHECKS)
    @scopes(required: [READ_CONFLUENCE_COMMENT], product: NO_GRANT_CHECKS)
    @scopes(required: [READ_CONFLUENCE_PAGE], product: NO_GRANT_CHECKS)
    @scopes(required: [READ_CONFLUENCE_SPACE], product: NO_GRANT_CHECKS)
}

type InfluentsNotificationQuery {
    """
    API for fetching user's notifications.
    """
    notificationFeed(
        filter: InfluentsNotificationFilter
        first: Int = 25
        after: String
    ): InfluentsNotificationFeedConnection!

    """
    API for fetching all notifications(not just the head notification) that belongs to a specific group.
    """
    notificationGroup(
        groupId: String!
        filter: InfluentsNotificationFilter
        first: Int = 25
        after: String
    ): InfluentsNotificationGroupConnection!

    """
    API for fetching user's un-read direct notification count.
    """
    unseenNotificationCount(workspaceId: String, product: String): Int!
}

input InfluentsNotificationFilter {
    workspaceId: String
    categoryFilter: InfluentsNotificationCategory
    readStateFilter: InfluentsNotificationReadState
    productFilter: String
}

enum InfluentsNotificationCategory {
    direct
    watching
}

enum InfluentsNotificationReadState {
    unread
    read
}

type InfluentsNotificationPageInfo {
    hasNextPage: Boolean!
    hasPreviousPage: Boolean!
    startCursor: String
    endCursor: String
}

type InfluentsNotificationAnalyticsAttribute {
    key: String
    value: String
}

type InfluentsNotificationActor {
    displayName: String
    ari: String
    avatarURL: String
}

type InfluentsNotificationDocument {
    format: String
    data: String
}

"""
A body item can be sent with two types of appearances, PRIMARY and QUOTED.
The latter can be used to for sending comment reply style notifications.
"""
type InfluentsNotificationBodyItem {
    type: String
    appearance: String
    document: InfluentsNotificationDocument
    author: InfluentsNotificationActor
}

enum InfluentsNotificationAppearance {
    DEFAULT
    PRIMARY
    LINK
    SUBTLE
    WARNING
    DANGER
}

"""
Call-to-action link where the notification is directed to.
"""
type InfluentsNotificationAction {
    title: String!
    url: String
    appearance: InfluentsNotificationAppearance!
}

"""
A path provides context for the entity.
This is particularly important if this is the first time the recipient has been made aware of the resource, or if multiple entities use the same or similar titles. The contents of the path are user defined, you may choose to end with the entity or not to.
"""
type InfluentsNotificationPath {
    title: String
    url: String
    iconUrl: String
}

"""
The Entity is what the notification relates to –
in most cases it’s the object (page, issue, pull request) that has been interacted with.
Clicking the title takes the user to the entity.
An entity can have a related icon.
"""
type InfluentsNotificationEntity {
    title: String
    iconUrl: String
    url: String
}

type InfluentsNotificationTemplateVariable {
    name: String!
    type: String!
    id: ID!
    fallback: String!
}

type InfluentsNotificationContent {
    type: String!
    message: String!
    url: String
    actor: InfluentsNotificationActor!
    entity: InfluentsNotificationEntity
    path: [InfluentsNotificationPath!]
    actions: [InfluentsNotificationAction!]
    bodyItems: [InfluentsNotificationBodyItem!]
    templateVariables: [InfluentsNotificationTemplateVariable!]
}

type InfluentsNotificationEntityModel{
    objectId: String!
    containerId: String
    workspaceId: String
    cloudId: String
}


"""
A single user notification item.
"""
type InfluentsNotificationItem {
    """
    Unique identity of the notification
    """
    notificationId: ID!
    timestamp: DateTime!
    """
    An optional field that contains Atlassian Entity details
    associated with the Notification event.
    """
    entityModel: InfluentsNotificationEntityModel
    content: InfluentsNotificationContent!
    readState: InfluentsNotificationReadState!
    category: InfluentsNotificationCategory!
    workspaceId: String
    analyticsAttributes: [InfluentsNotificationAnalyticsAttribute!]
}

"""
A grouped notification item containing the head notification item from each group
along with the count of items grouped/collapsed.
"""
type InfluentsNotificationHeadItem {
    groupId: ID!
    groupSize: Int!
    readStates: [String]!
    additionalActors: [InfluentsNotificationActor!]!
    additionalTypes: [String!]!
    headNotification: InfluentsNotificationItem!
    """
    Pagination cursor for excluding the current item from subsequent requests.
    """
    endCursor: String
}

"""
Notification Feed with pagination cursor
"""
type InfluentsNotificationFeedConnection {
    nodes: [InfluentsNotificationHeadItem!]!
    pageInfo: InfluentsNotificationPageInfo!
}

"""
Notification Group connection with pagination cursor
"""
type InfluentsNotificationGroupConnection {
    nodes: [InfluentsNotificationItem!]!
    pageInfo: InfluentsNotificationPageInfo!
}


# --------------------------------------- notifications_mutation_api
type Mutation {
    """
    Mutation object for Notification Experience
    """
    notifications: InfluentsNotificationMutation
    # Scopes are OR'd i.e acquiring any one of these scopes will allow the consumer consume activity data
    # For backwards compatibility with v2
    @scopes(required: [CONFLUENCE_ATLASSIAN_EXTERNAL], product: NO_GRANT_CHECKS)
    @scopes(required: [JIRA_ATLASSIAN_EXTERNAL], product: NO_GRANT_CHECKS)
    # Granular scopes so consumers are not forced to acquire the above "wider" ones
    @scopes(required: [READ_JIRA_WORK], product: NO_GRANT_CHECKS)
    @scopes(required: [READ_CONFLUENCE_BLOGPOST], product: NO_GRANT_CHECKS)
    @scopes(required: [READ_CONFLUENCE_COMMENT], product: NO_GRANT_CHECKS)
    @scopes(required: [READ_CONFLUENCE_PAGE], product: NO_GRANT_CHECKS)
    @scopes(required: [READ_CONFLUENCE_SPACE], product: NO_GRANT_CHECKS)
}

type InfluentsNotificationMutation {
    """
    API for marking the state of notifications(that belong to a particular product and category) as Read.

    With this endpoint clients can implement 'markAllAsRead' functionality.
    Only one before query parameter (beforeInclusive or beforeInclusiveTimestamp) .
    """
    markNotificationsAsRead(
        category: InfluentsNotificationCategory
        """
        Which product the notifications should be from. If omitted, the results are from any product.
        """
        product: String

        """
        Notifications will only be marked from the workspace with the specified workspace id.
        """
        workspaceId: String

        """
        The notificationId of the notification to mark and all older ones before the specified notification (INCLUSIVE).
        """
        beforeInclusive: String

        """
        Mark all notifications older than this timestamp in ISO-8601 format (INCLUSIVE).
        Format: date-time
        """
        beforeInclusiveTimestamp: String

    ): String

    """
    API for marking the state of notifications(that belong to a particular product and category) as unread.

    With this endpoint clients can implement 'markAllAsUnRead' functionality.
    Only one before query parameter (beforeInclusive or beforeInclusiveTimestamp) .
    """
    markNotificationsAsUnread(
        category: InfluentsNotificationCategory
        """
        Which product the notifications should be from. If omitted, the results are from any product.
        """
        product: String

        """
        Notifications will only be marked from the workspace with the specified workspace id.
        """
        workspaceId: String

        """
        The notificationId of the notification to mark and all older ones before the specified notification (INCLUSIVE).
        """
        beforeInclusive: String

        """
        Mark all notifications older than this timestamp in ISO-8601 format (INCLUSIVE).
        Format: date-time
        """
        beforeInclusiveTimestamp: String

    ): String


    """
    API for marking grouped notifications as read.
    With this endpoint clients can implement 'markAllAsRead' functionality for grouped notifications.
    """
    markNotificationsByGroupIdAsRead(
        category: InfluentsNotificationCategory
        """
        groupId for marking all notifications belonging to a group as read.
        """
        groupId: String

        """
        The notificationId of the notification to mark and all older ones (INCLUSIVE).
        """
        beforeInclusive: String

    ): String


    """
    API for marking grouped notifications as unread.
    With this endpoint clients can implement 'markAllAsUnRead' functionality for grouped notifications.
    """
    markNotificationsByGroupIdAsUnread(
        category: InfluentsNotificationCategory
        """
        groupId for marking grouped notifications  as unread.
        """
        groupId: String

        """
        The notificationId of the notification to mark and all older ones (INCLUSIVE).
        """
        beforeInclusive: String

    ): String

    """
    API for marking the notifications specified by ids as read.
    """
    markNotificationsByIdsAsRead(
        """
        The list of notifications specified by ids in which the state should be changed.
        Min items: 1
        Max items: 100
        Unique items: true
        """
        ids: [String!]!
    ): String

    """
    API for marking the state of notifications specified by ids as unread
    """
    markNotificationsByIdsAsUnread(
        """
        The list of notifications specified by ids in which the state should be changed.
        Min items: 1
        Max items: 100
        Unique items: true
        """
        ids: [String!]!
    ): String

    """
    API for archiving the notifications specified by ids.
    Note: Notifications will be removed from the datastore.
    """
    archiveNotifications(
        """
        The list of notifications specified by ids.
        Min items: 1
        Max items: 100
        Unique items: true
        """
        ids: [String!]!
    ): String

    """
    API for clearning unseen notification count for a user.
    """
    clearUnseenCount(
        """
        Specific product for which unseen notifications should be marked as seen. If omitted, notifications
        from all products will be marked as seen.
        """
        product: String

        """
        Specific workspace/cloudid for which unseen notifications should be marked as seen. If omitted, notifications
        from all workspaces will be marked as seen.
        """
        workspaceId: String

    ): String
}
        '''
        when:
        // todo: this is causing an NPE
        def changes = calcDiff(oldSdl, newSdl)
        then:
        noExceptionThrown()
    }

    def "applied directive argument deleted interface field "() {
        given:
        def oldSdl = '''
        directive @d(arg1:String) on FIELD_DEFINITION
        
        type Query implements I{
            foo: String 
        }
        interface I {
            foo: String @d(arg1: "foo")
        }
        '''
        def newSdl = '''
        directive @d(arg1:String) on FIELD_DEFINITION
        
        type Query implements I{
            foo: String 
        }
        interface I {
            foo: String @d
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def argumentDeletions = (changes.interfaceDifferences["I"] as InterfaceModification).getDetails(AppliedDirectiveArgumentDeletion)
        def location = argumentDeletions[0].locationDetail as AppliedDirectiveInterfaceFieldLocation
        location.interfaceName == "I"
        location.fieldName == "foo"
        argumentDeletions[0].argumentName == "arg1"
    }

    def "applied directive added input object field "() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INPUT_FIELD_DEFINITION
        input I {
            a: String
        }
        type Query {
            foo(arg: I): String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on INPUT_FIELD_DEFINITION
        input I {
            a: String @d(arg: "foo")
        }
        type Query {
            foo(arg: I): String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def appliedDirective = (changes.inputObjectDifferences["I"] as InputObjectModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveInputObjectFieldLocation).inputObjectName == "I"
        (appliedDirective[0].locationDetail as AppliedDirectiveInputObjectFieldLocation).fieldName == "a"
        appliedDirective[0].name == "d"
    }

    def "applied directive added object field"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on FIELD_DEFINITION
        
        type Query {
            foo: String 
        }
        '''
        def newSdl = '''
        directive @d(arg: String)  on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg: "foo")
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def appliedDirective = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldLocation).objectName == "Query"
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldLocation).fieldName == "foo"
        appliedDirective[0].name == "d"
    }

    def "applied directive argument value changed object field "() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg: "foo1")
        }
        '''
        def newSdl = '''
        directive @d(arg: String)  on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg: "foo2")
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def argumentValueModifications = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveArgumentValueModification)
        (argumentValueModifications[0].locationDetail as AppliedDirectiveObjectFieldLocation).objectName == "Query"
        (argumentValueModifications[0].locationDetail as AppliedDirectiveObjectFieldLocation).fieldName == "foo"
        argumentValueModifications[0].argumentName == "arg"
        argumentValueModifications[0].oldValue == '"foo1"'
        argumentValueModifications[0].newValue == '"foo2"'
    }

    def "applied directive argument name changed object field"() {
        given:
        def oldSdl = '''
        directive @d(arg1:String, arg2: String) on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg1: "foo")
        }
        '''
        def newSdl = '''
        directive @d(arg1: String, arg2: String)  on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg2: "foo")
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def argumentRenames = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveArgumentRename)
        def location = argumentRenames[0].locationDetail as AppliedDirectiveObjectFieldLocation
        location.objectName == "Query"
        location.fieldName == "foo"
        argumentRenames[0].oldName == "arg1"
        argumentRenames[0].newName == "arg2"
    }

    def "applied directive argument deleted object field"() {
        given:
        def oldSdl = '''
        directive @d(arg1:String) on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg1: "foo")
        }
        '''
        def newSdl = '''
        directive @d(arg1: String)  on FIELD_DEFINITION
        
        type Query {
            foo: String @d
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def argumentDeletions = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveArgumentDeletion)
        def location = argumentDeletions[0].locationDetail as AppliedDirectiveObjectFieldLocation
        location.objectName == "Query"
        location.fieldName == "foo"
        argumentDeletions[0].argumentName == "arg1"
    }

    def "applied directive added input object"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INPUT_OBJECT
        input I {
            a: String
        }
        type Query {
            foo(arg: I): String 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on INPUT_OBJECT
        input I @d(arg: "foo") {
            a: String
        }
        type Query {
            foo(arg: I): String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def appliedDirective = (changes.inputObjectDifferences["I"] as InputObjectModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveInputObjectLocation).name == "I"
        appliedDirective[0].name == "d"
    }

    def "applied directive added object"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on OBJECT
        
        type Query {
            foo: String 
        }
        '''
        def newSdl = '''
        directive @d(arg: String)  on OBJECT
        
        type Query @d(arg: "foo") {
            foo: String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def appliedDirective = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectLocation).name == "Query"
        appliedDirective[0].name == "d"
    }

    def "applied directive added interface"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INTERFACE
        
        type Query implements I{
            foo: String 
        }
        interface I {
            foo: String
        }
        '''
        def newSdl = '''
        directive @d(arg: String) on INTERFACE
        
        type Query implements I {
            foo: String 
        }
        interface I @d(arg: "foo") {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def appliedDirective = (changes.interfaceDifferences["I"] as InterfaceModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveInterfaceLocation).name == "I"
        appliedDirective[0].name == "d"
    }

    def "applied directive added union"() {
        given:
        def oldSdl = '''
        directive @d(arg: String) on UNION
        type Query {
            foo: U 
        }
        union U = A | B
        type A { a: String }
        type B { b: String }
        '''
        def newSdl = '''
        directive @d(arg: String) on UNION
        type Query {
            foo: U 
        }
        union U @d(arg: "foo") = A | B
        type A { a: String }
        type B { b: String }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences["U"] instanceof UnionModification
        def appliedDirective = (changes.unionDifferences["U"] as UnionModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveUnionLocation).name == "U"
        appliedDirective[0].name == "d"
    }

    def "applied directive added scalar"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on SCALAR
        scalar DateTime  
        type Query {
            foo: DateTime 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on SCALAR
        scalar DateTime  @d(arg: "foo")
        type Query {
            foo: DateTime 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.scalarDifferences["DateTime"] instanceof ScalarModification
        def appliedDirective = (changes.scalarDifferences["DateTime"] as ScalarModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveScalarLocation).name == "DateTime"
        appliedDirective[0].name == "d"
    }

    def "applied directive added enum"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ENUM
        enum E { A, B }
        type Query {
            foo: E 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ENUM
        enum E @d(arg: "foo") { A, B }
        type Query {
            foo: E 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumModification
        def appliedDirective = (changes.enumDifferences["E"] as EnumModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveEnumLocation).name == "E"
        appliedDirective[0].name == "d"
    }

    def "applied directive added enum value"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ENUM_VALUE
        enum E { A, B }
        type Query {
            foo: E 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ENUM_VALUE
        enum E  { A, B @d(arg: "foo") }
        type Query {
            foo: E 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumModification
        def appliedDirective = (changes.enumDifferences["E"] as EnumModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveEnumValueLocation).enumName == "E"
        (appliedDirective[0].locationDetail as AppliedDirectiveEnumValueLocation).valueName == "B"
        appliedDirective[0].name == "d"
    }

    def "applied directive added object field argument"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        type Query {
            foo(arg: String) : String 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        type Query {
            foo(arg: String @d(arg: "foo")) : String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def appliedDirective = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldArgumentLocation).objectName == "Query"
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldArgumentLocation).fieldName == "foo"
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldArgumentLocation).argumentName == "arg"
        appliedDirective[0].name == "d"
    }

    def "applied directive added interface field argument"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        type Query implements I {
            foo(arg: String) : String 
        }
        interface I {
            foo(arg: String): String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        type Query implements I {
            foo(arg: String) : String 
        }
        interface I {
            foo(arg: String @d): String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def appliedDirective = (changes.interfaceDifferences["I"] as InterfaceModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveInterfaceFieldArgumentLocation).interfaceName == "I"
        (appliedDirective[0].locationDetail as AppliedDirectiveInterfaceFieldArgumentLocation).fieldName == "foo"
        (appliedDirective[0].locationDetail as AppliedDirectiveInterfaceFieldArgumentLocation).argumentName == "arg"
        appliedDirective[0].name == "d"
    }

    def "applied directive added directive argument "() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        directive @d2(arg:String) on ARGUMENT_DEFINITION 
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        directive @d2(arg:String @d) on ARGUMENT_DEFINITION 
        type Query {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d2"] instanceof DirectiveModification
        def appliedDirective = (changes.directiveDifferences["d2"] as DirectiveModification).getDetails(AppliedDirectiveAddition)
        (appliedDirective[0].locationDetail as AppliedDirectiveDirectiveArgumentLocation).directiveName == "d2"
        (appliedDirective[0].locationDetail as AppliedDirectiveDirectiveArgumentLocation).argumentName == "arg"
        appliedDirective[0].name == "d"
    }

    def "applied directive deleted object"() {
        given:
        def oldSdl = '''
        directive @d(arg: String)  on OBJECT
        
        type Query @d(arg: "foo") {
            foo: String 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on OBJECT
        
        type Query {
            foo: String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def appliedDirective = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectLocation).name == "Query"
        appliedDirective[0].name == "d"
    }

    def "applied directive deleted directive argument "() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        directive @d2(arg:String @d) on ARGUMENT_DEFINITION 
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        directive @d2(arg:String) on ARGUMENT_DEFINITION 
        type Query {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.directiveDifferences["d2"] instanceof DirectiveModification
        def appliedDirective = (changes.directiveDifferences["d2"] as DirectiveModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveDirectiveArgumentLocation).directiveName == "d2"
        (appliedDirective[0].locationDetail as AppliedDirectiveDirectiveArgumentLocation).argumentName == "arg"
        appliedDirective[0].name == "d"
    }

    def "applied directive deleted enum"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ENUM
        enum E @d(arg: "foo") { A, B }
        type Query {
            foo: E 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ENUM
        enum E { A, B }
        type Query {
            foo: E 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumModification
        def diff = changes.enumDifferences["E"] as EnumModification

        diff.getDetails().size() == 1

        def appliedDirective = diff.getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveEnumLocation).name == "E"
        appliedDirective[0].name == "d"
    }

    def "applied directive deleted enum value"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ENUM_VALUE
        enum E  { A, B @d(arg: "foo") }
        type Query {
            foo: E 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ENUM_VALUE
        enum E { A, B }
        type Query {
            foo: E 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.enumDifferences["E"] instanceof EnumModification
        def appliedDirective = (changes.enumDifferences["E"] as EnumModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveEnumValueLocation).enumName == "E"
        (appliedDirective[0].locationDetail as AppliedDirectiveEnumValueLocation).valueName == "B"
        appliedDirective[0].name == "d"
    }


    def "applied directive deleted input object"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INPUT_OBJECT
        input I @d(arg: "foo") {
            a: String
        }
        type Query {
            foo(arg: I): String 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on INPUT_OBJECT
        input I {
            a: String
        }
        type Query {
            foo(arg: I): String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def appliedDirective = (changes.inputObjectDifferences["I"] as InputObjectModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveInputObjectLocation).name == "I"
        appliedDirective[0].name == "d"
    }

    def "applied directive deleted input object field "() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on INPUT_FIELD_DEFINITION
        input I {
            a: String @d(arg: "foo")
        }
        type Query {
            foo(arg: I): String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on INPUT_FIELD_DEFINITION
        input I {
            a: String
        }
        type Query {
            foo(arg: I): String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.inputObjectDifferences["I"] instanceof InputObjectModification
        def appliedDirective = (changes.inputObjectDifferences["I"] as InputObjectModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveInputObjectFieldLocation).inputObjectName == "I"
        (appliedDirective[0].locationDetail as AppliedDirectiveInputObjectFieldLocation).fieldName == "a"
        appliedDirective[0].name == "d"
    }

    def "applied directive deleted interface"() {
        given:
        def oldSdl = '''
        directive @d(arg: String) on INTERFACE
        
        type Query implements I {
            foo: String 
        }
        interface I @d(arg: "foo") {
            foo: String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on INTERFACE
        
        type Query implements I{
            foo: String 
        }
        interface I {
            foo: String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def appliedDirective = (changes.interfaceDifferences["I"] as InterfaceModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveInterfaceLocation).name == "I"
        appliedDirective[0].name == "d"
    }


    def "applied directive deleted interface field argument"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        type Query implements I {
            foo(arg: String) : String 
        }
        interface I {
            foo(arg: String @d): String
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        type Query implements I {
            foo(arg: String) : String 
        }
        interface I {
            foo(arg: String): String
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.interfaceDifferences["I"] instanceof InterfaceModification
        def appliedDirective = (changes.interfaceDifferences["I"] as InterfaceModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveInterfaceFieldArgumentLocation).interfaceName == "I"
        (appliedDirective[0].locationDetail as AppliedDirectiveInterfaceFieldArgumentLocation).fieldName == "foo"
        (appliedDirective[0].locationDetail as AppliedDirectiveInterfaceFieldArgumentLocation).argumentName == "arg"
        appliedDirective[0].name == "d"
    }

    def "applied directive deleted object field"() {
        given:
        def oldSdl = '''
        directive @d(arg: String)  on FIELD_DEFINITION
        
        type Query {
            foo: String @d(arg: "foo")
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on FIELD_DEFINITION
        
        type Query {
            foo: String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def appliedDirective = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldLocation).objectName == "Query"
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldLocation).fieldName == "foo"
        appliedDirective[0].name == "d"
    }

    def "applied directive deleted object field argument"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        type Query {
            foo(arg: String @d(arg: "foo")) : String 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on ARGUMENT_DEFINITION 
        type Query {
            foo(arg: String) : String 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.objectDifferences["Query"] instanceof ObjectModification
        def appliedDirective = (changes.objectDifferences["Query"] as ObjectModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldArgumentLocation).objectName == "Query"
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldArgumentLocation).fieldName == "foo"
        (appliedDirective[0].locationDetail as AppliedDirectiveObjectFieldArgumentLocation).argumentName == "arg"
        appliedDirective[0].name == "d"
    }


    def "applied directive deleted scalar"() {
        given:
        def oldSdl = '''
        directive @d(arg:String) on SCALAR
        scalar DateTime  @d(arg: "foo")
        type Query {
            foo: DateTime 
        }
        '''
        def newSdl = '''
        directive @d(arg:String) on SCALAR
        scalar DateTime  
        type Query {
            foo: DateTime 
        }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.scalarDifferences["DateTime"] instanceof ScalarModification
        def appliedDirective = (changes.scalarDifferences["DateTime"] as ScalarModification).getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveScalarLocation).name == "DateTime"
        appliedDirective[0].name == "d"
    }

    def "applied directive deleted union"() {
        given:
        def oldSdl = '''
        directive @d(arg: String) on UNION
        type Query {
            foo: U 
        }
        union U @d(arg: "foo") = A | B
        type A { a: String }
        type B { b: String }
        '''
        def newSdl = '''
        directive @d(arg: String) on UNION
        type Query {
            foo: U 
        }
        union U = A | B
        type A { a: String }
        type B { b: String }
        '''
        when:
        def changes = calcDiff(oldSdl, newSdl)
        then:
        changes.unionDifferences.keySet() == ["U"] as Set
        changes.unionDifferences["U"] instanceof UnionModification
        def diff = changes.unionDifferences["U"] as UnionModification
        diff.details.size() == 1

        def appliedDirective = diff.getDetails(AppliedDirectiveDeletion)
        (appliedDirective[0].locationDetail as AppliedDirectiveUnionLocation).name == "U"
        appliedDirective[0].name == "d"
    }


    EditOperationAnalysisResult calcDiff(
            String oldSdl,
            String newSdl
    ) {
        def oldSchema = TestUtil.schema(oldSdl)
        def newSchema = TestUtil.schema(newSdl)
        def changes = new SchemaDiffing().diffAndAnalyze(oldSchema, newSchema)
        return changes
    }

}
