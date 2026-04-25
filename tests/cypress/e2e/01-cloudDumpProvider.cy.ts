import {DocumentNode} from 'graphql';

describe('Jahia Cloud Dump Provider', () => {
    const configPath = '/jahia/administration/cloudDumpProvider';
    const defaultMountPath = '/sites/systemsite/files/cloud-dumps';
    const hardcodedDumpPath = '/var/tmp/cloud';

    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getSettings: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getSettings.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const saveSettings: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/mutation/saveSettings.graphql');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const getDumpFile: DocumentNode = require('graphql-tag/loader!../fixtures/graphql/query/getDumpFile.graphql');

    before(() => {
        cy.login();
    });

    after(() => {
        cy.apollo({
            mutation: saveSettings,
            variables: {mountPath: defaultMountPath}
        });
    });

    // ─── GraphQL API ─────────────────────────────────────────────────────────────

    describe('GraphQL API', () => {
        it('returns settings fields via query', () => {
            cy.apollo({query: getSettings})
                .its('data.cloudDumpSettings')
                .should(s => {
                    expect(s).to.have.property('mountPath');
                    expect(s).to.have.property('dumpPath');
                });
        });

        it('mountPath is a non-empty JCR path', () => {
            cy.apollo({query: getSettings})
                .its('data.cloudDumpSettings.mountPath')
                .should('match', /^\/.+/);
        });

        it('dumpPath equals hardcoded /var/tmp/cloud', () => {
            cy.apollo({query: getSettings})
                .its('data.cloudDumpSettings.dumpPath')
                .should('eq', hardcodedDumpPath);
        });

        it('saves settings and returns true', () => {
            cy.apollo({
                mutation: saveSettings,
                variables: {mountPath: '/sites/systemsite/files/cloud-dumps-test'}
            })
                .its('data.cloudDumpSaveSettings')
                .should('eq', true);
        });

        it('saves settings and reads them back consistently', () => {
            const testPath = '/sites/systemsite/files/cloud-dumps-roundtrip';
            cy.apollo({
                mutation: saveSettings,
                variables: {mountPath: testPath}
            });
            cy.apollo({query: getSettings})
                .its('data.cloudDumpSettings.mountPath')
                .should('eq', testPath);
        });
    });

    // ─── Dump file access ────────────────────────────────────────────────────────

    describe('Dump file access', () => {
        before(() => {
            cy.login();
            cy.apollo({
                mutation: saveSettings,
                variables: {mountPath: defaultMountPath}
            });
        });

        it('mount point node exists in JCR', () => {
            cy.login();
            cy.apollo({query: getDumpFile, variables: {path: defaultMountPath}})
                .its('data.jcr.nodeByPath')
                .should(node => {
                    expect(node).to.not.be.null;
                    expect(node.primaryNodeType.name).to.eq('jnt:folder');
                });
        });
    });

    // ─── Admin UI — Configuration ─────────────────────────────────────────────────

    describe('Admin UI — Configuration', () => {
        it('shows the admin panel title', () => {
            cy.login();
            cy.visit(configPath);
            cy.contains('Jahia Cloud').should('be.visible');
        });

        it('shows the mount path input field', () => {
            cy.login();
            cy.visit(configPath);
            cy.get('#cdp-mount-path').should('be.visible');
        });

        it('shows the save button', () => {
            cy.login();
            cy.visit(configPath);
            cy.contains('button', 'Save settings').should('be.visible');
        });

        it('shows success alert after saving', () => {
            cy.login();
            cy.visit(configPath);
            cy.get('#cdp-mount-path').clear();
            cy.get('#cdp-mount-path').type('/sites/systemsite/files/cloud-dumps-ui-test');
            cy.contains('button', 'Save settings').click();
            cy.get('[class*="cdp_alert--success"]').should('be.visible');
        });
    });
});
