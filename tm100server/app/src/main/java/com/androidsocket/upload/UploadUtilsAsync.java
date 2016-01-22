package com.androidsocket.upload;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

import android.os.AsyncTask;

/**
 * 文件上传
 * 
 * @author sh
 *
 */
public class UploadUtilsAsync extends AsyncTask<String, Integer, String> {
	/** 上传的参数 **/
	private Map<String, String> paramMap;
	/** 要上传的文件 的路径 **/
	private ArrayList<String> paths;
	// 图片上传成功之后的回调
	private OnSuccessListener listener;
	/** 服务器路径 **/
	private String url;
	private String [] uploadURL = {
			"http://192.168.0.110:8080/TSB-Dentist-Web/water_dentist/updateBind.do",
			"http://192.168.0.110:8080/TSB-Dentist-Web/water_dentist/brushingLog.do",
			"http://192.168.0.110:8080/TSB-Dentist-Web/water_dentist/uploadImg.do"};
	public static int UPLOAD_USER_INFO = 0x0;
	public static int UPLOAD_BRUSH_TIME = 0x01;
	public static int UPLOAD_IMG = 0x02;

	public UploadUtilsAsync(Map<String, String> paramMap,
			ArrayList<String> paths, int type) {
		if(uploadURL[type] != null) {
			url = uploadURL[type];
		}
		this.paramMap = paramMap;
		this.paths = paths;
	}

	public void setListener(OnSuccessListener listener) {
		this.listener = listener;
	}

	@Override
	protected void onPreExecute() {// 执行前的初始化
		super.onPreExecute();
	}

	@Override
	protected String doInBackground(String... params) {
		return uploadImage();
	}

	@Override
	protected void onProgressUpdate(Integer... values) {
		super.onProgressUpdate(values);
	}

	@Override
	protected void onPostExecute(String result) {
		if (listener != null) {
			listener.onSuccess(result);
		}
		super.onPostExecute(result);
	}

	public String uploadImage() {
		String res = "";
		HttpURLConnection conn = null;
		try {
			String BOUNDARY = "---------------------------"
					+ System.currentTimeMillis(); // boundary就是request头和上传文件内容的分隔符
			// String requestUrl = url + "?op=" + paramMap.get("op");
			String requestUrl = url; // 图片上传地址
			URL url = new URL(requestUrl);
			conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(30000);
			// 允许Input、Output，不使用Cache
			conn.setDoInput(true);
			conn.setDoOutput(true);
			conn.setUseCaches(false);
			// 设置以POST方式进行传送
			conn.setRequestMethod("POST");
			// 设置RequestProperty
			conn.setRequestProperty("accept", "*/*");
			conn.setRequestProperty("Connection", "Keep-Alive");
			conn.setRequestProperty("Charset", "UTF-8");
			conn.setRequestProperty("Content-Type",
					"multipart/form-data;boundary=" + BOUNDARY);
			// 构造DataOutputStream流
			OutputStream out = new DataOutputStream(conn.getOutputStream());
			// text
			if (null != paramMap && !paramMap.isEmpty()) {
				StringBuffer sf = new StringBuffer();
				for (String inputName : paramMap.keySet()) {
					String inputValue = (String) paramMap.get(inputName);
					if (inputValue == null) {
						continue;
					}
					sf.append("\r\n").append("--").append(BOUNDARY)
							.append("\r\n");
					sf.append("Content-Disposition: form-data; name=\""
							+ inputName + "\"\r\n\r\n");
					sf.append(inputValue);
				}
				out.write(sf.toString().getBytes("utf-8"));
			}
			// file
			if (paths != null && !paths.isEmpty()) {

				for (int i = 0; i < paths.size(); i++) {
					String path = paths.get(i);
					File file = new File(path);
					StringBuffer strBuf = new StringBuffer();
					strBuf.append("\r\n").append("--").append(BOUNDARY)
							.append("\r\n");
					strBuf.append("Content-Disposition: form-data; name=\""
							+ file.getName() + "\"; filename=\""
							+ file.getName() + "\"\r\n");
					// strBuf.append("Content-Type:image/jpeg \r\n\r\n");
					strBuf.append("Content-Type: application/octet-stream \r\n\r\n");
					out.write(strBuf.toString().getBytes("utf-8"));
					DataInputStream in = new DataInputStream(
							new FileInputStream(file));
					int bytes = 0;
					byte[] bufferOut = new byte[1024];
					while ((bytes = in.read(bufferOut)) != -1) {
						out.write(bufferOut, 0, bytes);
					}
					in.close();
				}
			}
			byte[] endData = ("\r\n--" + BOUNDARY + "--\r\n").getBytes("utf-8");
			out.write(endData);
			out.flush();
			out.close();
			// 读取返回数据
			StringBuffer strBuf = new StringBuffer();
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					conn.getInputStream()));
			String line = null;
			while ((line = reader.readLine()) != null) {
				strBuf.append(line).append("\n");
			}
			res = strBuf.toString();
			reader.close();
			reader = null;
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (conn != null) {
				conn.disconnect();
				conn = null;
			}
		}
		return res;
	}

	/**
	 * 图片上传成功之后的回调
	 * 
	 * @author sh
	 *
	 */
	public interface OnSuccessListener {
		public void onSuccess(String result);
	}
}
