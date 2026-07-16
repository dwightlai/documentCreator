package cn.gov.document.platform.document.application;

import cn.gov.document.platform.document.domain.DocumentEntity;
import cn.gov.document.platform.document.domain.DocumentParagraphEntity;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfExporter {
    private static final int WIDTH=1240,HEIGHT=1754,MARGIN_X=112,TOP=118,BOTTOM=128,BODY_SIZE=31,LINE_HEIGHT=59;

    public byte[] export(DocumentEntity document,List<DocumentParagraphEntity> paragraphs){
        Font bodyFont=font(Font.PLAIN,BODY_SIZE);Font titleFont=font(Font.BOLD,46);
        List<TextLine> lines=new ArrayList<>();for(DocumentParagraphEntity paragraph:paragraphs)lines.addAll(wrap(paragraph.getPlainText(),bodyFont,WIDTH-MARGIN_X*2));
        int firstPageCapacity=(HEIGHT-BOTTOM-(TOP+160))/LINE_HEIGHT;int otherCapacity=(HEIGHT-BOTTOM-TOP)/LINE_HEIGHT;
        List<List<TextLine>> pages=new ArrayList<>();int cursor=0,page=0;while(cursor<lines.size()||pages.isEmpty()){int capacity=page++==0?firstPageCapacity:otherCapacity;int end=Math.min(lines.size(),cursor+capacity);pages.add(new ArrayList<>(lines.subList(cursor,end)));cursor=end;}
        try(PDDocument pdf=new PDDocument();ByteArrayOutputStream output=new ByteArrayOutputStream()){
            pdf.getDocumentInformation().setTitle(document.getTitle());pdf.getDocumentInformation().setSubject("AI 辅助生成，文稿版本 V"+document.getCurrentVersion());
            for(int index=0;index<pages.size();index++){BufferedImage image=renderPage(document,pages.get(index),index,pages.size(),titleFont,bodyFont);PDPage pdfPage=new PDPage(PDRectangle.A4);pdf.addPage(pdfPage);PDImageXObject xobject=LosslessFactory.createFromImage(pdf,image);
                try(PDPageContentStream stream=new PDPageContentStream(pdf,pdfPage)){stream.drawImage(xobject,0,0,PDRectangle.A4.getWidth(),PDRectangle.A4.getHeight());}}
            pdf.save(output);return output.toByteArray();
        }catch(IOException exception){throw new IllegalStateException("PDF 导出失败",exception);}
    }

    private BufferedImage renderPage(DocumentEntity document,List<TextLine> lines,int page,int total,Font titleFont,Font bodyFont){BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();try{g.setColor(Color.WHITE);g.fillRect(0,0,WIDTH,HEIGHT);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);int y=TOP;
            if(page==0){g.setColor(new Color(155,36,30));g.fillRect(WIDTH/2-31,74,62,4);g.setColor(new Color(20,20,20));g.setFont(titleFont);FontMetrics metrics=g.getFontMetrics();g.drawString(document.getTitle(),Math.max(MARGIN_X,(WIDTH-metrics.stringWidth(document.getTitle()))/2),150);y=TOP+160;}
            g.setColor(new Color(25,25,25));g.setFont(bodyFont);for(TextLine line:lines){g.drawString(line.text(),MARGIN_X+line.indent(),y);y+=LINE_HEIGHT;}
            g.setColor(new Color(150,150,150));g.drawLine(MARGIN_X,HEIGHT-94,WIDTH-MARGIN_X,HEIGHT-94);g.setFont(font(Font.PLAIN,18));String footer="AI 辅助生成  ·  文稿版本 V"+document.getCurrentVersion()+"  ·  "+(page+1)+" / "+total;g.drawString(footer,MARGIN_X,HEIGHT-58);
        }finally{g.dispose();}return image;}

    private List<TextLine> wrap(String value,Font font,int width){String text=value==null?"":value.strip();if(text.isEmpty())return List.of(new TextLine("",0));BufferedImage probe=new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB);Graphics2D g=probe.createGraphics();try{g.setFont(font);FontMetrics metrics=g.getFontMetrics();List<TextLine> result=new ArrayList<>();StringBuilder line=new StringBuilder();boolean first=true;int indent=BODY_SIZE*2;
            for(int point:text.codePoints().toArray()){String character=new String(Character.toChars(point));int available=width-(first?indent:0);if(line.length()>0&&metrics.stringWidth(line+character)>available){result.add(new TextLine(line.toString(),first?indent:0));line.setLength(0);first=false;}line.append(character);}if(line.length()>0)result.add(new TextLine(line.toString(),first?indent:0));return result;
        }finally{g.dispose();}}
    private Font font(int style,int size){String[] names={"Noto Serif CJK SC","Noto Sans CJK SC","Microsoft YaHei","SimSun","Serif"};for(String name:names){Font candidate=new Font(name,style,size);if(candidate.canDisplayUpTo("政务公文智能审校，依法依规。")<0)return candidate;}return new Font(Font.SERIF,style,size);}
    private record TextLine(String text,int indent){ }
}
