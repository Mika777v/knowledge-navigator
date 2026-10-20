package com.example.knavi.shared.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 業務処理の外で起きる共通の例外を Problem Details に変換する */
@RestControllerAdvice
class ApiExceptionHandler {

    /** 同時更新（If-Match の確認後に他の人が更新した場合） */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail onOptimisticLock() {
        return ApiErrors.preconditionFailed().getBody();
    }

    /** 一意制約などの違反（同時に同じコードで登録した場合など）。内部の制約名は返さない */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail onDataIntegrityViolation() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "データの整合性に反するため保存できませんでした。");
    }
}
