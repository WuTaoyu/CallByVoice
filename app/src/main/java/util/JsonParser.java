package util;

/**
 * Created by Administrator on 2016/6/27.
 */

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Json结果解析类
 */
public class JsonParser {

    public static List<String> parseGrammarResult(String json) {
        List<String> ret = new ArrayList<String>();
        try {
            JSONTokener tokener = new JSONTokener(json);
            JSONObject joResult = new JSONObject(tokener);

            JSONArray words = joResult.getJSONArray("ws");
            for (int i = 0; i < words.length(); i++) {
                JSONArray items = words.getJSONObject(i).getJSONArray("cw");
                for(int j = 0; j < items.length(); j++)
                {
                    JSONObject obj = items.getJSONObject(j);
                    if(obj.getString("w").contains("nomatch"))
                    {
                        ret.add("没有匹配结果.");
                        return ret;
                    }
                    ret.add(obj.getString("w"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            ret.add("没有匹配结果.");
        }
        return ret;
    }

}
