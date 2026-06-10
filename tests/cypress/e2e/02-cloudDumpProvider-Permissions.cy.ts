import {DocumentNode} from 'graphql';
import {createUser, deleteUser, grantRoles} from '@jahia/cypress';

/**
 * Regression tests for the fine-grained `heapDumpsAdmin` permission.
 *
 * These guard against the gate being silently removed or mismatched across the stack:
 *  - Backend: `@GraphQLRequiresPermission("heapDumpsAdmin")` on the `cloudDumpSettings` query
 *    and `cloudDumpSaveSettings` mutation, enforced on the JCR root node.
 *  - Data source: `JahiaCloudDumpDataSource.getPrivilegesNames()` checks
 *    `getNode("/").hasPermission("heapDumpsAdmin")` to expose dump files.
 *  - Frontend: `requiredPermission: 'heapDumpsAdmin'` in register.jsx gates the admin route
 *    (relaxed from the hardcoded `'admin'` so the fine-grained role works end-to-end).
 *  - RBAC content: the module ships the assignable `jahia-cloud-threads-heap-dumps-provider-administrator`
 *    role (src/main/import/roles.xml) granting only `administrationAccess heapDumpsAdmin`.
 *
 * The "allowed" user is granted that role and nothing else — never `admin` — so the tests prove
 * fine-grained granularity, not merely that a full administrator can pass.
 */
describe('Jahia Cloud Dump Provider — permission enforcement', () => {
    const ROLE_NAME = 'jahia-cloud-threads-heap-dumps-provider-administrator';
    const DENIED_USER = 'cdpDeniedUser';
    const ALLOWED_USER = 'cdpAllowedUser';
    const PASSWORD = 'CdpPerm9PwdTest';
    const ADMIN_PATH = '/jahia/administration/cloudDump';

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getSettings: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getSettings.graphql');

    const errorsOf = (result: {graphQLErrors?: Array<{message: string}>; errors?: Array<{message: string}>}) =>
        result.graphQLErrors ?? result.errors ?? [];

    const querySettingsAs = (username: string) => {
        cy.apolloClient({username, password: PASSWORD});
        return cy.apollo({query: getSettings});
    };

    before(() => {
        cy.login();
        createUser(DENIED_USER, PASSWORD);
        createUser(ALLOWED_USER, PASSWORD);
        // The annotation resolves the permission on the JCR root node, so grant the
        // module-shipped single-permission role on `/`.
        grantRoles('/', [ROLE_NAME], ALLOWED_USER, 'USER');
    });

    after(() => {
        cy.apolloClient(); // reset the current Apollo client back to root
        cy.login();
        deleteUser(DENIED_USER);
        deleteUser(ALLOWED_USER);
    });

    describe('GraphQL API authorization', () => {
        it('denies the gated query for a user without the permission', () => {
            querySettingsAs(DENIED_USER).then((result: never) => {
                const errs = errorsOf(result);
                expect(errs, 'denial errors').to.have.length.greaterThan(0);
                expect(errs.map((e: {message: string}) => e.message).join(' ')).to.contain('Permission denied');
            });
        });

        it('allows the gated query for a user granted only the module permission', () => {
            querySettingsAs(ALLOWED_USER).then((result: never) => {
                expect(errorsOf(result), 'should have no errors').to.have.length(0);
                const settings = (result as {data: {cloudDumpSettings: {mountPath: string; dumpPath: string}}}).data.cloudDumpSettings;
                expect(settings).to.have.property('mountPath');
                expect(settings).to.have.property('dumpPath');
            });
        });
    });

    describe('Admin UI authorization', () => {
        it('hides the admin panel from a user without the permission', () => {
            cy.login(DENIED_USER, PASSWORD);
            cy.visit(ADMIN_PATH, {failOnStatusCode: false});
            cy.contains('Jahia Cloud').should('not.exist');
        });

        it('shows the admin panel to a user granted only the module permission', () => {
            cy.login(ALLOWED_USER, PASSWORD);
            cy.visit(ADMIN_PATH);
            cy.contains('Jahia Cloud').should('be.visible');
        });
    });
});
