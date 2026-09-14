package com.golmok.market.global.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.Errors;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.List;

/**
 * 컨트롤러에서 발생한 모든 예외를 ErrorResponse 형식으로 변환한다.
 *
 * 원칙
 * - 4xx 는 클라이언트가 고칠 수 있는 문제이므로 원인을 메시지로 알려준다.
 * - 5xx 는 내부 정보(SQL, 클래스명 등)가 새지 않도록 고정 메시지만 내리고,
 *   원인은 스택트레이스와 함께 로그로만 남긴다.
 *
 * 주의: Spring Security 필터에서 발생하는 401/403 은 DispatcherServlet 앞에서
 * 끝나므로 여기까지 오지 않는다. 인증 단계에서 EntryPoint/AccessDeniedHandler 를
 * 따로 두고 ErrorResponse 를 재사용해야 한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ---------- 우리가 의도적으로 던진 예외 ----------

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        log.info("BusinessException: {} - {}", e.getErrorCode(), e.getMessage());
        return respond(e.getErrorCode(), ErrorResponse.of(e.getErrorCode(), e.getMessage()));
    }

    // ---------- 입력 검증 ----------

    /** @Valid @RequestBody 검증 실패 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException e) {
        return invalidInput(toFieldErrors(e.getBindingResult()));
    }

    /** @RequestParam @Min(1) 처럼 컨트롤러 파라미터에 직접 붙인 제약 위반 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        e.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors parameterErrors) {
                errors.addAll(toFieldErrors(parameterErrors));
                return;
            }
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error ->
                    errors.add(new ErrorResponse.FieldError(name, error.getDefaultMessage())));
        });
        return invalidInput(errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        return invalidInput(List.of(new ErrorResponse.FieldError(e.getParameterName(), "필수 파라미터입니다.")));
    }

    /** ?size=abc 처럼 타입 변환이 안 되는 경우 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return invalidInput(List.of(new ErrorResponse.FieldError(e.getName(), "형식이 올바르지 않습니다.")));
    }

    /** JSON 문법 오류, enum 에 없는 값 등. 파서 메시지는 내부 구현이 드러나므로 내리지 않는다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        log.info("요청 본문 파싱 실패: {}", e.getMessage());
        return respond(ErrorCode.INVALID_INPUT, ErrorResponse.of(ErrorCode.INVALID_INPUT, "요청 본문 형식이 올바르지 않습니다."));
    }

    // ---------- HTTP 규약 ----------

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorResponse.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    // ---------- 엔티티가 던지는 표준 예외 (임시 안전망) ----------
    // 현재 엔티티들은 비즈니스 규칙 위반 시 IllegalArgument/IllegalStateException 을 던진다.
    // 메시지가 사용자용 한국어로 작성돼 있어 그대로 내린다.
    // 다만 "참여자가 아님"(403 이어야 함)도 여기로 와서 400 이 되므로,
    // 각 도메인을 구현할 때 BusinessException 으로 교체해야 한다.

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.info("IllegalArgumentException: {}", e.getMessage());
        return respond(ErrorCode.INVALID_INPUT, ErrorResponse.of(ErrorCode.INVALID_INPUT, e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        log.info("IllegalStateException: {}", e.getMessage());
        return respond(ErrorCode.INVALID_STATE, ErrorResponse.of(ErrorCode.INVALID_STATE, e.getMessage()));
    }

    // ---------- 나머지 전부 ----------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // 404(NoResourceFound), 406 등 Spring MVC 예외는 대부분 ErrorResponse 인터페이스로
        // 자기 HTTP 상태를 알고 있다. 이를 500 으로 덮어쓰면 안 되므로 상태를 그대로 쓴다.
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            ErrorCode errorCode = ErrorCode.fromStatus(status);
            if (status.is4xxClientError()) {
                log.info("Spring MVC 4xx: {} - {}", status.value(), e.getMessage());
                return ResponseEntity.status(status).body(ErrorResponse.of(errorCode));
            }
        }
        log.error("처리되지 않은 예외", e);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }

    // ---------- 내부 ----------

    private ResponseEntity<ErrorResponse> invalidInput(List<ErrorResponse.FieldError> errors) {
        return respond(ErrorCode.INVALID_INPUT, ErrorResponse.of(ErrorCode.INVALID_INPUT, errors));
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode errorCode, ErrorResponse body) {
        return ResponseEntity.status(errorCode.getStatus()).body(body);
    }

    private List<ErrorResponse.FieldError> toFieldErrors(Errors bindingErrors) {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        bindingErrors.getFieldErrors().forEach(error ->
                errors.add(new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage())));
        // 필드가 아닌 객체 단위 검증(예: 비밀번호 확인 일치)은 객체 이름을 field 로 쓴다.
        bindingErrors.getGlobalErrors().forEach(error ->
                errors.add(new ErrorResponse.FieldError(error.getObjectName(), error.getDefaultMessage())));
        return errors;
    }
}
