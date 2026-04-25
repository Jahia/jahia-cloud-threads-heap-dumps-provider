import {registry} from '@jahia/ui-extender';
import React from 'react';
import CloudDumpProviderAdmin from './CloudDumpProvider';

export default () => {
    console.debug('%c jahia-cloud-threads-heap-dumps-provider: activation in progress', 'color: #006633');
    registry.add('adminRoute', 'cloudDump', {
        targets: ['administration-server-systemHealth:99'],
        requiredPermission: 'admin',
        isSelectable: true,
        label: 'jahia-cloud-threads-heap-dumps-provider:label.main_menu_entry',
        render: () => React.createElement(CloudDumpProviderAdmin)
    });
};
