package com.java.yincools.web;

import com.java.yincools.domain.JobService;
import com.java.yincools.domain.model.Job;
import com.java.yincools.domain.model.WorkType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Offline job capture: the New Job page's JS posts here instead of the
 * normal form endpoint when it wants a JSON response it can retry safely.
 * clientId is generated on the phone before the first attempt, so a retry
 * after a lost response is recognized as already-processed (see
 * JobService.createJobIdempotent) instead of creating a duplicate job.
 *
 * CSRF-exempt (see SecurityConfig) -- safe because this only accepts
 * application/json, which browsers can't send cross-origin without a CORS
 * preflight, and no CORS policy is configured to allow one. Still requires
 * the same session/remember-me login as everything else.
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobApiController {

    private final JobService jobService;

    @PostMapping
    public JobResponse createJob(@RequestBody JobRequest req) {
        boolean sharedPartsCost = Boolean.TRUE.equals(req.sharedPartsCost());

        Job job = jobService.createJobIdempotent(req.clientId(), req.customerName(), req.customerPhone(),
                req.vehicleId(), req.vehicleDescription(), req.vehiclePlateNumber(), req.vehicleDescription(),
                req.workType(), req.charge(), req.partsCost(), req.partsNote(), sharedPartsCost, req.paid());

        return new JobResponse(job.getId(), job.getCachedCharge(), job.getCachedPaid(), job.getCachedBalance());
    }

    public record JobRequest(
            String clientId,
            String customerName,
            String customerPhone,
            Long vehicleId,
            String vehicleDescription,
            String vehiclePlateNumber,
            Boolean sharedPartsCost,
            WorkType workType,
            BigDecimal charge,
            BigDecimal partsCost,
            String partsNote,
            BigDecimal paid
    ) {
    }

    public record JobResponse(Long id, BigDecimal cachedCharge, BigDecimal cachedPaid, BigDecimal cachedBalance) {
    }
}
