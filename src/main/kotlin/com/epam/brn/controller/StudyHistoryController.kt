package com.epam.brn.controller

import com.epam.brn.constant.BrnPath
import com.epam.brn.dto.StudyHistoryDto
import com.epam.brn.service.StudyHistoryService
import io.swagger.annotations.Api
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(BrnPath.STUDY_HISTORIES)
@Api(value = BrnPath.STUDY_HISTORIES, description = "Contains actions over the results of finished exercise")
class StudyHistoryController(@Autowired val studyHistoryService: StudyHistoryService) {

    @PostMapping
    fun saveOrReplaceStudyHistory(@Validated @RequestBody studyHistoryDto: StudyHistoryDto): ResponseEntity<StudyHistoryDto> {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            studyHistoryService.saveOrReplaceStudyHistory(studyHistoryDto)
        )
    }

    @PatchMapping
    fun patchStudyHistory(@Validated @RequestBody studyHistoryDto: StudyHistoryDto): ResponseEntity<String> {
        studyHistoryService.patchStudyHistory(studyHistoryDto)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    @PutMapping
    fun replaceStudyHistory(@Validated @RequestBody studyHistoryDto: StudyHistoryDto): ResponseEntity<StudyHistoryDto> {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            studyHistoryService.replaceStudyHistory(studyHistoryDto)
        )
    }
}