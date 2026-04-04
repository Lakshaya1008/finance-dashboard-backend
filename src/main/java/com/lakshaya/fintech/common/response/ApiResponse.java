package com.lakshaya.fintech.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

/**
 * Universal response envelope.
 *
 * Success: { "data": T,    "message": "Success" }
 * Error:   { "code": "...", "message": "..." }
 *
 * @JsonInclude(NON_NULL) ensures unused fields are omitted from JSON.
 * Success responses never show "code": null.
 * Error responses never show "data": null.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

	private T data;
	private String message;
	private String code;

	public static <T> ApiResponse<T> success(T data) {
		ApiResponse<T> res = new ApiResponse<>();
		res.data = data;
		res.message = "Success";
		return res;
	}

	public static <T> ApiResponse<T> success(T data, String message) {
		ApiResponse<T> res = new ApiResponse<>();
		res.data = data;
		res.message = message;
		return res;
	}

	public static <T> ApiResponse<T> error(String code, String message) {
		ApiResponse<T> res = new ApiResponse<>();
		res.code = code;
		res.message = message;
		return res;
	}
}

