package com.enro;
import com.enro.ui.SquarifiedLayout;
import com.enro.storage.ExportService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LayoutTest {
 @Test void areaAndBounds(){var list=List.of(100L,70L,40L,10L);var rects=SquarifiedLayout.layout(list,1000,600);assertEquals(4,rects.size());assertEquals(600000,rects.stream().mapToDouble(r->r.width()*r.height()).sum(),0.001);for(var r:rects){assertTrue(r.x()>=0&&r.y()>=0);assertTrue(r.x()+r.width()<=1000.001&&r.y()+r.height()<=600.001);assertEquals(list.get(r.index())/220.0*600000,r.width()*r.height(),0.001);}}
 @Test void noZeroRectangles(){assertTrue(SquarifiedLayout.layout(List.of(0L),100,100).isEmpty());assertTrue(SquarifiedLayout.layout(List.of(1L),0,100).isEmpty());}
 @Test void csvProtectsFormulaAndQuotes(){assertEquals("\"'=1+1\"",ExportService.csv("=1+1"));assertEquals("\"a\"\"b\"",ExportService.csv("a\"b"));}
}
