import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Vector;

import javax.imageio.ImageIO;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import ImageProcess.*;
import Algorithm.*;

public class ImageSearch {
	//資料庫連線相關
	static Connection cn;
	static Statement stmt;
	static String imgdata = "./img";
	static String[] keyword = {"Animal","Landscape"};
	static int cnt;
	static String[] imageId,title,content,ori_imgurl,tb_imgurl;
	public static void ImageSearch(String keyword)
	{
		try {
			URL url = new URL(new String("https://ajax.googleapis.com/ajax/services/search/images?" +
					"v=1.0&q="+keyword+"&rsz=8").replace(" ", "%20"));
			URLConnection connection = url.openConnection();
			connection.addRequestProperty("Referer", "color");
			String line;
			StringBuilder builder = new StringBuilder();
			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			while((line = reader.readLine()) != null) {
				builder.append(line);
			}
			get_json(builder.toString());
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public static void get_json(String json_string)
	{
		try{
			JSONObject json = new JSONObject(json_string);
			JSONObject responseData = json.getJSONObject("responseData");
			JSONObject json2 = new JSONObject(responseData.toString());
			JSONArray result = json2.getJSONArray("results");
			cnt = result.length();
			//System.out.println("cnt:"+cnt);
			imageId = new String[cnt];
			title = new String[cnt];
			content = new String[cnt];
			ori_imgurl = new String[cnt];
			tb_imgurl  = new String[cnt];
			for(int i=0;i<cnt;i++){
				JSONObject jsonItem = result.getJSONObject(i);
				//System.out.println(jsonItem.getString("tbUrl"));
				title[i] = jsonItem.getString("titleNoFormatting");
				content[i] = jsonItem.getString("contentNoFormatting");
				ori_imgurl[i] = jsonItem.getString("url");
				tb_imgurl[i] = jsonItem.getString("tbUrl");
				imageId[i] = jsonItem.getString("imageId");
				//System.out.println("ori:"+ori_imgurl[i]);
				//System.out.println("tb :"+tb_imgurl[i]);
			}
		}catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public static boolean download_img(String imgurl,String imgname,File classifi_dir)
	{
		URL url;
		boolean success = false;
		try {
			url = new URL(imgurl);
			FileOutputStream fos = new FileOutputStream(classifi_dir.toString().trim()+"/"+imgname+".jpg", false);
			InputStream is = url.openStream();
			int r = 0;
			int chunkSize = 1024 * 8;
			byte[] buf = new byte[chunkSize];
			int readLen;
			while ((readLen = is.read( buf, 0, buf.length)) != -1) {
				fos.write(buf, 0, readLen);  
			}
			is.close();
			fos.close();
			success=true;
		} catch (MalformedURLException e) {
			// TODO 自動產生的 catch 區塊
			e.printStackTrace();
		} catch (IOException e) {
			// TODO 自動產生的 catch 區塊
			e.printStackTrace();
		}
		return success;

	}
	public static void connect_db() throws SQLException
	{
		//連接資料庫
		String connectionString = "jdbc:sqlite:./test.db";
		cn = DriverManager.getConnection(connectionString);
		//SQL語句類
		stmt = cn.createStatement();
	}
	public static void close_db() throws SQLException
	{
		stmt.close();
		cn.close();
	}
	public static void initdb()
	{
		ResultSet rsExist;
		//測試資料庫是否連線
		try{
			Class.forName("org.sqlite.JDBC");
			System.out.println("Load sqlite Driver sucess!");
		}
		catch(java.lang.ClassNotFoundException e){
			System.out.println("Fail to Load sqlite Driver!");
			System.out.println(e.getMessage());
		}

		try{
			connect_db();
			//創建資料庫
			File testdb = new File("test.db");
			rsExist = stmt.executeQuery("select * from sqlite_master where type='table' and name ='Classification';");
			if(!rsExist.next())
			{
				System.out.println("create classification!");
				stmt.execute("CREATE TABLE if not exists Classification(cid int primary key,"
						+ "cname char(50),"
						+ "search_keyword char(50));");
				for(int i=0;i<keyword.length;i++)
				{
					String insert = "insert into Classification(cid,cname,search_keyword) values("+i+"," +
							"'"+keyword[i]+"','');";
					stmt.execute(insert);
				}
			}
			rsExist = stmt.executeQuery("select * from sqlite_master where type='table' and name ='imagedb';");
			if(!rsExist.next())
			{
				stmt.execute("CREATE TABLE if not exists imagedb(id int primary key, "
						+ "cid int references Classification(cid),"
						+ "title char(50),"
						+ "content text,"
						+ "ori_imgurl text,"
						+ "tb_imgurl text,"
						+ "imageId char(100),"
						+ "search_key char(50),"
						+ "time char(25));");
				stmt.execute("insert into imagedb(id,cid,title,content,ori_imgurl,tb_imgurl,imageId,search_key,time)" +
						" values(0,0,0,0,0,0,0,0,0);");
			}
			close_db();
		}catch(SQLException e){
			System.out.println("Init Database Fail!");
			System.out.println(e.getMessage());
		}catch(Exception e){
			System.out.println(e.toString());
		}

	}
	public static void main(String[] args) 
	{
		// TODO Auto-generated method stub
		//初始化資料庫設定
		initdb();
		ResultSet rs;
		String insert;
		File classifi_dir = null;
		String timeStamp ;
		long StartTime,EndTime;
		StartTime = System.currentTimeMillis();
		try{
			System.out.println("Start Search Image!");
			connect_db();
			for(int k=0;k<keyword.length;k++)
			{
				System.out.println("Search ["+keyword[k]+"]");
				ImageSearch(keyword[k]);
				for(int i=0;i<cnt;i++)
				{
					classifi_dir = new File(imgdata+"/"+keyword[k]+"/"+imageId[i]);
					if(!classifi_dir.exists())
						classifi_dir.mkdirs();
					//rs = stmt.executeQuery("select * from imagedb where ori_imgurl='"+ori_imgurl[i]+"';");
					rs = stmt.executeQuery("select * from imagedb where ori_imgurl='"+ori_imgurl[i]+"';");
					if(!rs.next())
					{
						Boolean action = download_img(tb_imgurl[i],imageId[i],classifi_dir);
						//System.out.println(action);
						if(action)
						{
							timeStamp = new SimpleDateFormat("yyyy/MM/dd-HH:mm:ss").format(Calendar.getInstance().getTime());
							insert = "insert into imagedb(id,cid,title,content,ori_imgurl,tb_imgurl,imageId,search_key,time)" +
									"values((select max(id)+1 from imagedb),"+(k+1)+",'"+title[i]+"','"+content[i]+"','"+ori_imgurl[i]+"','"+tb_imgurl[i]+"','"
									+imageId[i]+"','"+""+"','"+timeStamp+"');";
							stmt.execute(insert);
						}else
						{
							classifi_dir.delete();
						}
					}
				}

			}
			close_db();
			System.out.println("End Search Image!");
			EndTime = System.currentTimeMillis();
			System.out.println("執行時間："+((EndTime-StartTime)*0.001)+"秒");
		}
		catch(SQLException e){
			System.out.println("Fail！");
			System.out.println(e.getMessage());
		}
	}
}
