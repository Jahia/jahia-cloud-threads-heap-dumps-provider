import React, {useState} from 'react';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Loader, Typography} from '@jahia/moonstone';
import styles from './CloudDumpProvider.scss';
import {GET_SETTINGS, SAVE_SETTINGS} from './CloudDumpProvider.gql';

export const CloudDumpProviderAdmin = () => {
    const {t} = useTranslation('jahia-cloud-threads-heap-dumps-provider');
    const [saveStatus, setSaveStatus] = useState(null);
    const [mountPath, setMountPath] = useState('');

    const {loading} = useQuery(GET_SETTINGS, {
        fetchPolicy: 'network-only',
        onCompleted: data => {
            const s = data?.cloudDumpSettings;
            if (s) {
                setMountPath(s.mountPath ?? '');
            }
        }
    });

    const {data} = useQuery(GET_SETTINGS, {fetchPolicy: 'cache-first'});
    const dumpPath = data?.cloudDumpSettings?.dumpPath ?? '';

    const [saveSettings, {loading: saving}] = useMutation(SAVE_SETTINGS);

    const handleSave = async () => {
        setSaveStatus(null);
        try {
            const result = await saveSettings({variables: {mountPath}});
            setSaveStatus(result.data?.cloudDumpSaveSettings ? 'success' : 'error');
        } catch (err) {
            console.error('Failed to save settings:', err);
            setSaveStatus('error');
        }
    };

    if (loading) {
        return (
            <div className={styles.cdp_loading}>
                <Loader size="big"/>
            </div>
        );
    }

    return (
        <div className={styles.cdp_container}>
            <div className={styles.cdp_formSection}>
                <div className={styles.cdp_header}>
                    <h2>{t('label.title')}</h2>
                </div>

                <div className={styles.cdp_description}>
                    <Typography>{t('label.description')}</Typography>
                </div>

                <div className={styles.cdp_form}>
                    <div className={styles.cdp_fieldGroup}>
                        <label className={styles.cdp_label}>{t('label.dumpPath')}</label>
                        <span className={styles.cdp_readOnly}>{dumpPath}</span>
                        <span className={styles.cdp_hint}>{t('label.dumpPathHint')}</span>
                    </div>

                    <div className={styles.cdp_fieldGroup}>
                        <label className={styles.cdp_label} htmlFor="cdp-mount-path">
                            {t('label.mountPath')}
                        </label>
                        <input
                            type="text"
                            id="cdp-mount-path"
                            className={styles.cdp_input}
                            value={mountPath}
                            onChange={e => {
                                setMountPath(e.target.value);
                                setSaveStatus(null);
                            }}
                        />
                        <span className={styles.cdp_hint}>{t('label.mountPathHint')}</span>
                    </div>
                </div>

                <div className={styles.cdp_actions}>
                    {saveStatus === 'success' && (
                        <div className={`${styles.cdp_alert} ${styles['cdp_alert--success']}`}>
                            {t('label.saveSuccess')}
                        </div>
                    )}
                    {saveStatus === 'error' && (
                        <div className={`${styles.cdp_alert} ${styles['cdp_alert--error']}`}>
                            {t('label.saveError')}
                        </div>
                    )}
                    <Button
                        label={t('label.save')}
                        variant="primary"
                        isDisabled={saving || !mountPath.trim()}
                        onClick={handleSave}
                    />
                </div>
            </div>
        </div>
    );
};

export default CloudDumpProviderAdmin;
