package util;

import java.util.List;

/**
 * Created by Administrator on 2016/7/11.
 */
public class ListParser {
    public static String TurnListToString(List<String> list){
        StringBuffer str = new StringBuffer();
        if(list!=null&&list.size()>0) {
            for (int i = 0; i < list.size(); i++) {
                str.append(list.get(i));
            }
        }
        return str.toString();
    }
}
