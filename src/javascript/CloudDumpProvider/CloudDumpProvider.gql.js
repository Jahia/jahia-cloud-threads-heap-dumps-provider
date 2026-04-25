import {gql} from '@apollo/client';

export const GET_SETTINGS = gql`
    query {
        cloudDumpSettings {
            mountPath
            dumpPath
        }
    }
`;

export const SAVE_SETTINGS = gql`
    mutation CloudDumpSaveSettings($mountPath: String!) {
        cloudDumpSaveSettings(mountPath: $mountPath)
    }
`;
