package com.example.Vee.myapplication.backend;

import java.util.HashMap;

import com.google.gson.Gson;

public class Util {

	public static String toJsonPair(String key, String vl) {
		HashMap<String, Object> obj = new HashMap<String, Object>();
		obj.put(key, vl);
		return new Gson().toJson(obj);
	}

	public static String toJson(Object o) {
		return new Gson().toJson(o);
	}

}
