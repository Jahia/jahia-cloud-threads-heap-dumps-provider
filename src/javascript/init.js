import {registry} from '@jahia/ui-extender';
import register from './CloudDumpProvider/register';
import i18next from 'i18next';

export default function () {
    registry.add('callback', 'jahia-cloud-threads-heap-dumps-provider', {
        targets: ['jahiaApp-init:50'],
        callback: async () => {
            await i18next.loadNamespaces('jahia-cloud-threads-heap-dumps-provider', () => {
                console.debug('%c jahia-cloud-threads-heap-dumps-provider: i18n namespace loaded', 'color: #006633');
            });
            register();
            console.debug('%c jahia-cloud-threads-heap-dumps-provider: activation completed', 'color: #006633');
        }
    });
}
