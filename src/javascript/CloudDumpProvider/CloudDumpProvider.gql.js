import {gql} from '@apollo/client';

export const GET_SETTINGS = gql`
    query {
        cloudDump {
            settings {
                mountPath
                dumpPath
            }
        }
    }
`;

export const SAVE_SETTINGS = gql`
    mutation CloudDumpSaveSettings($mountPath: String!) {
        cloudDump {
            saveSettings(mountPath: $mountPath)
        }
    }
`;
