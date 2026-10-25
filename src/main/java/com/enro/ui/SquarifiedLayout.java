package com.enro.ui;

import java.util.*;

/** Pure geometry; no JavaFX dependency. Input must be sorted by descending area. */
public final class SquarifiedLayout {
    public record Rect(int index,double x,double y,double width,double height) {}
    private record Area(int index,double area){}
    public static List<Rect> layout(List<Long> sizes,double width,double height){
        if(width<=0||height<=0)return List.of();double total=sizes.stream().mapToDouble(n->Math.max(0,n)).sum();if(total<=0)return List.of();
        var remaining=new ArrayList<Area>();for(int i=0;i<sizes.size();i++)if(sizes.get(i)>0)remaining.add(new Area(i,sizes.get(i)/total*width*height));remaining.sort(Comparator.comparingDouble(Area::area).reversed());
        var out=new ArrayList<Rect>();var row=new ArrayList<Area>();double x=0,y=0,w=width,h=height;int i=0;
        while(i<remaining.size()){
            Area next=remaining.get(i);double edge=Math.min(w,h);var test=new ArrayList<>(row);test.add(next);
            if(row.isEmpty()||worst(test,edge)<=worst(row,edge)){row.add(next);i++;}
            else{double used=place(row,x,y,w,h,out);if(w>=h){x+=used;w=Math.max(0,w-used);}else{y+=used;h=Math.max(0,h-used);}row.clear();}
        }if(!row.isEmpty())place(row,x,y,w,h,out);return out;
    }
    private static double worst(List<Area> row,double edge){if(row.isEmpty()||edge<=0)return Double.POSITIVE_INFINITY;double sum=0,min=Double.MAX_VALUE,max=0;for(var a:row){sum+=a.area;min=Math.min(min,a.area);max=Math.max(max,a.area);}return Math.max(edge*edge*max/(sum*sum),sum*sum/(edge*edge*min));}
    private static double place(List<Area> row,double x,double y,double w,double h,List<Rect> out){double sum=row.stream().mapToDouble(Area::area).sum();if(w>=h){double dw=h<=0?0:sum/h;double cy=y;for(var a:row){double dh=dw<=0?0:a.area/dw;out.add(new Rect(a.index,x,cy,dw,dh));cy+=dh;}return dw;}double dh=w<=0?0:sum/w;double cx=x;for(var a:row){double dw=dh<=0?0:a.area/dh;out.add(new Rect(a.index,cx,y,dw,dh));cx+=dw;}return dh;}
}
