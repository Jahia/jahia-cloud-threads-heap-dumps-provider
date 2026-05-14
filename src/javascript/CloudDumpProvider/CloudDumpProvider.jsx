import React, {useEffect, useRef, useState} from 'react';
import {useMutation, useQuery} from '@apollo/client';
import {useTranslation} from 'react-i18next';
import {Button, Loader, Typography} from '@jahia/moonstone';
import styles from './CloudDumpProvider.scss';
import {GET_SETTINGS, SAVE_SETTINGS} from './CloudDumpProvider.gql';

const buildJContentUrl = jcrPath => {
    const match = jcrPath.match(/^\/sites\/([^/]+)\/files(\/.*)?$/);
    if (!match) {
        return null;
    }

    const siteKey = match[1];
    const rest = match[2] ?? '';
    return `/jahia/jcontent/${siteKey}/en/media/files${rest}`;
};

export const CloudDumpProviderAdmin = () => {
    const {t} = useTranslation('jahia-cloud-threads-heap-dumps-provider');
    const [saveStatus, setSaveStatus] = useState(null);
    const [mountPath, setMountPath] = useState('');
    const saveBtnRef = useRef(null);
    const mountPathRef = useRef(null);

    useEffect(() => {
        document.title = `${t('label.title')} — Jahia Administration`;
    }, [t]);

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
    const jContentUrl = buildJContentUrl(mountPath);

    const [saveSettings, {loading: saving}] = useMutation(SAVE_SETTINGS);

    const handleSave = async () => {
        setSaveStatus(null);
        try {
            const result = await saveSettings({variables: {mountPath}});
            const status = result.data?.cloudDumpSaveSettings ? 'success' : 'error';
            setSaveStatus(status);
            setTimeout(() => {
                if (status === 'success') {
                    saveBtnRef.current?.focus();
                } else {
                    mountPathRef.current?.focus();
                }
            }, 50);
        } catch (err) {
            console.error('Failed to save settings:', err);
            setSaveStatus('error');
            setTimeout(() => mountPathRef.current?.focus(), 50);
        }
    };

    const saveLiveMsg = saveStatus === 'success' ? t('label.saveSuccess') :
        saveStatus === 'error' ? t('label.saveError') : '';

    if (loading) {
        return (
            <div className={styles.cdp_loading} role="status">
                <span className={styles.cdp_sr_only}>{t('label.loading')}</span>
                <Loader size="big"/>
            </div>
        );
    }

    return (
        <div className={styles.cdp_container}>
            {/* Separate always-in-DOM live regions so AT never sees role mutations */}
            <div role="status" aria-live="polite" aria-atomic="true" className={styles.cdp_sr_only}>
                {saveStatus === 'success' ? saveLiveMsg : ''}
            </div>
            <div role="alert" aria-live="assertive" aria-atomic="true" className={styles.cdp_sr_only}>
                {saveStatus === 'error' ? saveLiveMsg : ''}
            </div>

            <div className={styles.cdp_formSection}>
                <div className={styles.cdp_header}>
                    <h2>{t('label.title')}</h2>
                </div>

                <div className={styles.cdp_description}>
                    <Typography>{t('label.description')}</Typography>
                </div>

                <div className={styles.cdp_form}>
                    {/* Read-only field: use dl/dt/dd for label/value association without htmlFor */}
                    <dl className={styles.cdp_fieldGroup}>
                        <dt className={styles.cdp_label}>{t('label.dumpPath')}</dt>
                        <dd className={styles.cdp_readOnly}>{dumpPath}</dd>
                        <dd className={styles.cdp_hint}>{t('label.dumpPathHint')}</dd>
                    </dl>

                    <div className={styles.cdp_fieldGroup}>
                        <label className={styles.cdp_label} htmlFor="cdp-mount-path">
                            {t('label.mountPath')}
                        </label>
                        <input
                            ref={mountPathRef}
                            type="text"
                            id="cdp-mount-path"
                            className={styles.cdp_input}
                            value={mountPath}
                            required
                            aria-required="true"
                            aria-invalid={saveStatus === 'error' ? 'true' : undefined}
                            aria-describedby={saveStatus === 'error' ? 'cdp-mount-hint cdp-mount-error' : 'cdp-mount-hint'}
                            autoComplete="off"
                            onChange={e => {
                                setMountPath(e.target.value);
                                setSaveStatus(null);
                            }}
                            onKeyDown={e => {
                                if (e.key === 'Enter' && e.ctrlKey && mountPath.trim()) {
                                    handleSave();
                                }
                            }}
                        />
                        <span id="cdp-mount-hint" className={styles.cdp_hint}>{t('label.mountPathHint')}</span>
                        {saveStatus === 'error' && (
                            <span id="cdp-mount-error" className={styles.cdp_sr_only} role="alert">{t('label.saveError')}</span>
                        )}
                    </div>
                </div>

                <div className={styles.cdp_actions}>
                    {saveStatus === 'success' && (
                        <div aria-hidden="true" className={`${styles.cdp_alert} ${styles['cdp_alert--success']}`}>
                            <span className={styles.cdp_alertIcon} aria-hidden="true">✓</span> {t('label.saveSuccess')}
                        </div>
                    )}
                    {saveStatus === 'error' && (
                        <div aria-hidden="true" className={`${styles.cdp_alert} ${styles['cdp_alert--error']}`}>
                            <span className={styles.cdp_alertIcon} aria-hidden="true">✕</span> {t('label.saveError')}
                        </div>
                    )}
                    <div className={styles.cdp_buttons}>
                        <Button
                            buttonRef={saveBtnRef}
                            label={t('label.save')}
                            variant="primary"
                            isDisabled={saving || !mountPath.trim()}
                            onClick={handleSave}
                        />
                        <Button
                            label={t('label.browseInJContent')}
                            variant="secondary"
                            isDisabled={!jContentUrl}
                            onClick={() => window.open(jContentUrl, '_blank')}
                            aria-describedby="cdp-new-tab-hint"
                        />
                        <span id="cdp-new-tab-hint" className={styles.cdp_sr_only}>{t('label.opensInNewTab')}</span>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default CloudDumpProviderAdmin;
