package com.portfolio.insurance.batch.api;

import com.portfolio.insurance.batch.BatchJobs;

import java.util.List;

/** Display information and accepted parameters of the jobs shown on the monitoring screen. */
enum BatchJobCatalog {

    POLICY_RENEWAL(BatchJobs.POLICY_RENEWAL, "Poliçe yenileme",
            "30 gün içinde süresi dolacak poliçeler için PL/SQL prim motoruyla yenileme teklifi üretir.",
            List.of(BatchJobs.PARAM_RUN_DATE)),
    OVERDUE_INSTALLMENT(BatchJobs.OVERDUE_INSTALLMENT, "Gecikmiş taksitler",
            "Vadesi geçen taksitleri gecikmede yapar, 2+ gecikmede poliçeyi askıya alır, bildirim oluşturur.",
            List.of(BatchJobs.PARAM_RUN_DATE)),
    OVERDUE_INSTALLMENT_PLSQL(BatchJobs.OVERDUE_INSTALLMENT_PLSQL, "Gecikmiş taksitler (PL/SQL toplu)",
            "Aynı işi tek PL/SQL çağrısında BULK COLLECT / FORALL ile yapar; büyük veri ve karşılaştırma için.",
            List.of(BatchJobs.PARAM_RUN_DATE)),
    POLICY_EXPIRATION(BatchJobs.POLICY_EXPIRATION, "Süre sonu",
            "Süresi dolan poliçeleri kapatır: yenilenmiş olanlar 'Yenilendi', diğerleri 'Süresi doldu'.",
            List.of(BatchJobs.PARAM_RUN_DATE)),
    CLAIM_IMPORT(BatchJobs.CLAIM_IMPORT, "Toplu hasar ihbarı",
            "CSV dosyasından hasar ihbarlarını içe aktarır; hatalı satırları raporlar.",
            List.of(BatchJobs.PARAM_INPUT_FILE)),
    MONTHLY_PRODUCTION_REPORT(BatchJobs.MONTHLY_PRODUCTION_REPORT, "Aylık üretim raporu",
            "Ay içinde tanzim edilen poliçelerin ürün ve acente bazlı prim üretimini Excel'e yazar.",
            List.of(BatchJobs.PARAM_MONTH)),
    COLLECTION_REQUEST_EXPORT(BatchJobs.COLLECTION_REQUEST_EXPORT, "Banka tahsilat talebi",
            "Vadesi yaklaşan taksitler için bankaya gönderilecek sabit uzunluklu tahsilat talep dosyasını üretir.",
            List.of(BatchJobs.PARAM_RUN_DATE)),
    COLLECTION_RESULT_IMPORT(BatchJobs.COLLECTION_RESULT_IMPORT, "Banka tahsilat sonucu",
            "Bankanın sonuç dosyasını trailer kontrolüyle doğrular, paralel bölümlerle PL/SQL üzerinden işler.",
            List.of(BatchJobs.PARAM_INPUT_FILE));

    private final String jobName;
    private final String title;
    private final String description;
    private final List<String> parameters;

    BatchJobCatalog(String jobName, String title, String description, List<String> parameters) {
        this.jobName = jobName;
        this.title = title;
        this.description = description;
        this.parameters = parameters;
    }

    String jobName() {
        return jobName;
    }

    String title() {
        return title;
    }

    String description() {
        return description;
    }

    List<String> parameters() {
        return parameters;
    }
}
