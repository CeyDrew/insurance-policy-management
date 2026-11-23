package com.portfolio.insurance.batch;

/** Job names and job parameter keys shared by job configurations, the REST API and the scheduler. */
public final class BatchJobs {

    public static final String POLICY_RENEWAL = "policyRenewalJob";
    public static final String OVERDUE_INSTALLMENT = "overdueInstallmentJob";
    public static final String OVERDUE_INSTALLMENT_PLSQL = "overdueInstallmentPlsqlJob";
    public static final String POLICY_EXPIRATION = "policyExpirationJob";
    public static final String CLAIM_IMPORT = "claimImportJob";
    public static final String MONTHLY_PRODUCTION_REPORT = "monthlyProductionReportJob";
    public static final String COLLECTION_REQUEST_EXPORT = "collectionRequestExportJob";
    public static final String COLLECTION_RESULT_IMPORT = "collectionResultImportJob";

    /** Business date the job runs for (LocalDate); defaults to today. */
    public static final String PARAM_RUN_DATE = "runDate";
    /** Report month, yyyy-MM; defaults to the previous month. */
    public static final String PARAM_MONTH = "month";
    /** Absolute path of the claim CSV file. */
    public static final String PARAM_INPUT_FILE = "inputFile";
    /** Original name of the uploaded file (informational, non-identifying). */
    public static final String PARAM_ORIGINAL_FILE_NAME = "originalFileName";
    /** SHA-256 of a bank result file: the identifying parameter of collectionResultImportJob (one instance per file). */
    public static final String PARAM_FILE_HASH = "fileHash";
    /** Parallel partitions of the result import; non-identifying: the file alone identifies the job instance. */
    public static final String PARAM_PARTITIONS = "partitions";
    /** Makes every manual launch a new job instance so jobs can be re-run with the same business parameters. */
    public static final String PARAM_LAUNCHED_AT = "launchedAt";
    public static final String PARAM_LAUNCHED_BY = "launchedBy";

    /** Recorded as created_by/updated_by on rows written by batch jobs. */
    public static final String BATCH_USER = "batch";

    /** Exit code of a step/job that finished but skipped invalid items. */
    public static final String COMPLETED_WITH_SKIPS = "COMPLETED_WITH_SKIPS";

    private BatchJobs() {
    }
}
