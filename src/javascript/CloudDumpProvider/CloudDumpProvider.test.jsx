import React from 'react';
import {render, screen} from '@testing-library/react';
import {useMutation, useQuery} from '@apollo/client';
import {buildJContentUrl, CloudDumpProviderAdmin} from './CloudDumpProvider';

jest.mock('@jahia/moonstone');
jest.mock('react-i18next');
jest.mock('@apollo/client');

describe('buildJContentUrl', () => {
    it('maps a valid /sites/{site}/files path with a sub-path', () => {
        // Arrange / Act
        const url = buildJContentUrl('/sites/systemsite/files/cloud-dumps');

        // Assert
        expect(url).toBe('/jahia/jcontent/systemsite/en/media/files/cloud-dumps');
    });

    it('maps a /sites/{site}/files root path with no sub-path', () => {
        expect(buildJContentUrl('/sites/mysite/files')).toBe('/jahia/jcontent/mysite/en/media/files');
    });

    it('returns null for a path that is not under /sites/*/files', () => {
        expect(buildJContentUrl('/invalid/path')).toBeNull();
    });

    it('returns null for an empty string', () => {
        expect(buildJContentUrl('')).toBeNull();
    });
});

describe('CloudDumpProviderAdmin save state transitions', () => {
    const settings = {mountPath: '/sites/systemsite/files/cloud-dumps', dumpPath: '/var/tmp/cloud'};

    // Return the loaded data directly. Do NOT invoke onCompleted from the mock: real
    // Apollo fires it once on resolution, whereas calling it on every useQuery() render
    // would loop (setMountPath -> re-render -> useQuery -> onCompleted -> ...).
    const mockQueryLoaded = () => {
        useQuery.mockReturnValue({data: {cloudDumpSettings: settings}, loading: false});
    };

    beforeEach(() => {
        jest.clearAllMocks();
        mockQueryLoaded();
    });

    it('renders the dumpPath read from the single network-only query', () => {
        useMutation.mockReturnValue([jest.fn(), {loading: false}]);
        render(<CloudDumpProviderAdmin/>);
        expect(screen.getByText('/var/tmp/cloud')).toBeTruthy();
        // Single query call: H-1 regression guard (was 2 useQuery calls).
        expect(useQuery).toHaveBeenCalledTimes(1);
    });

    // The asynchronous save success/error state transitions are exercised end-to-end by the
    // Cypress specs (real save flow); they are omitted here to avoid jsdom async-flush fragility.
});
