package com.example.dynamicschedule.base;


import java.io.Serializable;


/**
 * 返回数据格式
 *
 */
public class ResultDTO implements Serializable {

	private int code;

	private String msg;

	private Object data;

	private int count;
	public ResultDTO(ResultCode code, Object result) {
		this.code = code.getCode();
		this.msg = code.getErrorDesc();
		this.data = result;
	}

	public ResultDTO(ResultCode code, String errorDesc, Object result) {
		this.code = code.getCode();
		this.msg = errorDesc;
		this.data = result;
	}

	public ResultDTO(int code, String errorDesc, Object result) {
		this.code = code;
		this.msg = errorDesc;
		this.data = result;
	}


}
