package com.psicosus.medicalrecord.service;

import com.psicosus.medicalrecord.dto.*;
import com.psicosus.medicalrecord.entity.InternshipHours;
import com.psicosus.medicalrecord.entity.MedicalRecord;
import com.psicosus.medicalrecord.event.SessionEndedEvent;
import com.psicosus.medicalrecord.repository.InternshipHoursRepository;
import com.psicosus.medicalrecord.repository.MedicalRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class MedicalRecordService {

    private final MedicalRecordRepository recordRepository;
    private final InternshipHoursRepository internshipHoursRepository;

    public MedicalRecordService(MedicalRecordRepository recordRepository,
                                 InternshipHoursRepository internshipHoursRepository) {
        this.recordRepository = recordRepository;
        this.internshipHoursRepository = internshipHoursRepository;
    }

    @Transactional
    public void onSessionEnded(SessionEndedEvent event) {
        recordRepository.save(MedicalRecord.builder()
                .patientId(event.patientId())
                .sessionId(event.sessionId())
                .studentId(event.studentId())
                .supervisorId(event.supervisorId())
                .clinicalSummary(event.clinicalSummary())
                .icd10(event.icd10())
                .referral(event.referral())
                .suggestedReturn(event.suggestedReturn())
                .build());

        internshipHoursRepository.save(InternshipHours.builder()
                .studentId(event.studentId())
                .sessionId(event.sessionId())
                .durationMinutes(event.durationMinutes())
                .sessionDate(event.sessionDate())
                .build());
    }

    @Transactional(readOnly = true)
    public List<PatientRecordResponse> byPatient(UUID patientId) {
        return recordRepository.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .map(r -> new PatientRecordResponse(r.getId(), r.getSessionId(), r.getClinicalSummary(),
                        r.getIcd10(), r.getReferral(), r.getSuggestedReturn(), r.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public InternshipHoursSummaryResponse internshipHours(UUID studentId) {
        List<InternshipHours> hours = internshipHoursRepository.findByStudentIdOrderBySessionDateDesc(studentId);

        long totalMinutes = hours.stream().mapToLong(InternshipHours::getDurationMinutes).sum();
        double totalHours = totalMinutes / 60.0;

        List<InternshipHoursRecord> records = hours.stream()
                .map(h -> new InternshipHoursRecord(h.getSessionId(), h.getDurationMinutes(), h.getSessionDate(), h.isValidated()))
                .toList();

        return new InternshipHoursSummaryResponse(studentId, totalMinutes, totalHours, records);
    }

    @Transactional
    public ValidateHoursResponse validate(UUID internshipHoursId, UUID supervisorId) {
        InternshipHours hours = internshipHoursRepository.findById(internshipHoursId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "internship hours entry not found"));

        hours.setValidated(true);
        hours.setValidatedBy(supervisorId);
        internshipHoursRepository.save(hours);

        return new ValidateHoursResponse(hours.getId(), hours.isValidated(), hours.getValidatedBy());
    }
}
