import {DocumentNode} from 'graphql';

describe('Jahia Cloud Dump Provider', () => {
    const configPath = '/jahia/administration/cloudDump';
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
                .its('data.cloudDump.settings')
                .should(s => {
                    expect(s).to.have.property('mountPath');
                    expect(s).to.have.property('dumpPath');
                });
        });

        it('mountPath is a non-empty JCR path', () => {
            cy.apollo({query: getSettings})
                .its('data.cloudDump.settings.mountPath')
                .should('match', /^\/.+/);
        });

        it('dumpPath equals hardcoded /var/tmp/cloud', () => {
            cy.apollo({query: getSettings})
                .its('data.cloudDump.settings.dumpPath')
                .should('eq', hardcodedDumpPath);
        });

        it('saves settings and returns true', () => {
            cy.apollo({
                mutation: saveSettings,
                variables: {mountPath: '/sites/systemsite/files/cloud-dumps-test'}
            })
                .its('data.cloudDump.saveSettings')
                .should('eq', true);
        });

        it('saves settings and reads them back consistently', () => {
            const testPath = '/sites/systemsite/files/cloud-dumps-roundtrip';
            cy.apollo({
                mutation: saveSettings,
                variables: {mountPath: testPath}
            }).then(() =>
                cy.apollo({query: getSettings})
                    .its('data.cloudDump.settings.mountPath')
                    .should('eq', testPath)
            );
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

        it('mount point root exists as a jnt:folder', () => {
            cy.apollo({query: getDumpFile, variables: {path: defaultMountPath}})
                .its('data.jcr.nodeByPath')
                .should(node => {
                    expect(node).to.not.be.null;
                    expect(node.primaryNodeType.name).to.eq('jnt:folder');
                });
        });

        it('heap sub-folder exists as a jnt:folder', () => {
            cy.apollo({query: getDumpFile, variables: {path: `${defaultMountPath}/heap`}})
                .its('data.jcr.nodeByPath')
                .should(node => {
                    expect(node).to.not.be.null;
                    expect(node.primaryNodeType.name).to.eq('jnt:folder');
                });
        });

        it('heapdump.hprof exists as a jnt:file', () => {
            cy.apollo({query: getDumpFile, variables: {path: `${defaultMountPath}/heap/heapdump.hprof`}})
                .its('data.jcr.nodeByPath')
                .should(node => {
                    expect(node).to.not.be.null;
                    expect(node.primaryNodeType.name).to.eq('jnt:file');
                });
        });

        it('heapdump.hprof has a binary MIME type', () => {
            cy.apollo({query: getDumpFile, variables: {path: `${defaultMountPath}/heap/heapdump.hprof`}})
                .its('data.jcr.nodeByPath.descendant.property.value')
                .should('not.be.null');
        });

        it('thread sub-folder exists as a jnt:folder', () => {
            cy.apollo({query: getDumpFile, variables: {path: `${defaultMountPath}/thread`}})
                .its('data.jcr.nodeByPath')
                .should(node => {
                    expect(node).to.not.be.null;
                    expect(node.primaryNodeType.name).to.eq('jnt:folder');
                });
        });

        it('thread_dump.txt exists as a jnt:file', () => {
            cy.apollo({query: getDumpFile, variables: {path: `${defaultMountPath}/thread/thread_dump.txt`}})
                .its('data.jcr.nodeByPath')
                .should(node => {
                    expect(node).to.not.be.null;
                    expect(node.primaryNodeType.name).to.eq('jnt:file');
                });
        });

        it('thread_dump.txt has a text MIME type', () => {
            cy.apollo({query: getDumpFile, variables: {path: `${defaultMountPath}/thread/thread_dump.txt`}})
                .its('data.jcr.nodeByPath.descendant.property.value')
                .should('match', /^text\//);
        });

        it('folder with ISO timestamp name (colons) is accessible as jnt:folder', () => {
            // Regression: filenames containing ':' (e.g. ISO 8601 timestamps) caused
            // MalformedPathException because escapeNodePath() did not escape ':'.
            cy.apollo({
                query: getDumpFile,
                variables: {path: `${defaultMountPath}/modulesdump/2026-01-22T14%3A15%3A28.106Z_cluster_restart`}
            })
                .its('data.jcr.nodeByPath')
                .should(node => {
                    expect(node).to.not.be.null;
                    expect(node.primaryNodeType.name).to.eq('jnt:folder');
                });
        });
    });

    // ─── Admin UI — Configuration ─────────────────────────────────────────────────

    describe('Admin UI — Configuration', () => {
        beforeEach(() => {
            cy.login();
        });

        it('shows the admin panel title', () => {
            cy.visit(configPath);
            cy.contains('Jahia Cloud').should('be.visible');
        });

        it('shows the mount path input field', () => {
            cy.visit(configPath);
            cy.get('#cdp-mount-path').should('be.visible');
        });

        it('shows the save button', () => {
            cy.visit(configPath);
            cy.contains('button', 'Save settings').should('be.visible');
        });

        it('shows success alert after saving via button', () => {
            cy.visit(configPath);
            cy.get('#cdp-mount-path').clear();
            cy.get('#cdp-mount-path').type('/sites/systemsite/files/cloud-dumps-ui-test');
            cy.contains('button', 'Save settings').click();
            cy.get('[class*="cdp_alert--success"]').should('be.visible');
        });

        it('shows success alert after saving via Ctrl+Enter', () => {
            cy.visit(configPath);
            cy.get('#cdp-mount-path').clear();
            cy.get('#cdp-mount-path').type('/sites/systemsite/files/cloud-dumps-keyboard-test');
            cy.get('#cdp-mount-path').type('{ctrl+enter}');
            cy.get('[class*="cdp_alert--success"]').should('be.visible');
        });

        it('Ctrl+Enter does nothing when the field is empty', () => {
            cy.visit(configPath);
            cy.get('#cdp-mount-path').clear();
            cy.get('#cdp-mount-path').type('{ctrl+enter}');
            cy.get('[class*="cdp_alert--success"]').should('not.exist');
        });
    });

    // ─── Admin UI — Browse in jContent ───────────────────────────────────────────

    describe('Admin UI — Browse in jContent', () => {
        beforeEach(() => {
            cy.login();
        });

        it('shows the Browse in jContent button', () => {
            cy.visit(configPath);
            cy.contains('button', 'Browse in jContent').should('be.visible');
        });

        it('Browse in jContent button is enabled for a valid /sites/ mount path', () => {
            cy.visit(configPath);
            cy.get('#cdp-mount-path').clear();
            cy.get('#cdp-mount-path').type('/sites/systemsite/files/cloud-dumps');
            cy.contains('button', 'Browse in jContent').should('not.be.disabled');
        });

        it('Browse in jContent button is disabled when mount path is not a /sites/ path', () => {
            cy.visit(configPath);
            cy.get('#cdp-mount-path').clear();
            cy.get('#cdp-mount-path').type('/invalid/path');
            cy.contains('button', 'Browse in jContent').should('be.disabled');
        });

        it('Browse in jContent button opens the correct jContent URL', () => {
            cy.visit(configPath);
            cy.window().then(win => {
                cy.stub(win, 'open').as('windowOpen');
            });
            cy.get('#cdp-mount-path').clear();
            cy.get('#cdp-mount-path').type('/sites/systemsite/files/cloud-dumps');
            cy.contains('button', 'Browse in jContent').click();
            cy.get('@windowOpen').should(
                'have.been.calledWith',
                '/jahia/jcontent/systemsite/en/media/files/cloud-dumps',
                '_blank'
            );
        });
    });
});
