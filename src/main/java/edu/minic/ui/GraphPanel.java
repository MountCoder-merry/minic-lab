package edu.minic.ui;

import edu.minic.lab.Automata;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/** Java2D 有向状态图；滚轮缩放，拖动平移，双击适应窗口。 */
final class GraphPanel extends JPanel {
    private Automata.Machine machine;private final Map<Integer,Point2D.Double> points=new HashMap<>();
    private double scale=1,tx=50,ty=50;private boolean fit=true;private Point drag;
    GraphPanel(){
        setBackground(Color.WHITE);setToolTipText("");
        MouseAdapter mouse=new MouseAdapter(){
            @Override public void mousePressed(MouseEvent e){drag=e.getPoint();}
            @Override public void mouseDragged(MouseEvent e){fit=false;tx+=e.getX()-drag.x;ty+=e.getY()-drag.y;drag=e.getPoint();repaint();}
            @Override public void mouseClicked(MouseEvent e){if(e.getClickCount()==2){fit=true;repaint();}}
            @Override public void mouseWheelMoved(MouseWheelEvent e){double old=scale;scale=Math.max(0.08,Math.min(4,scale*Math.pow(1.12,-e.getPreciseWheelRotation())));tx=e.getX()-(e.getX()-tx)*scale/old;ty=e.getY()-(e.getY()-ty)*scale/old;fit=false;repaint();}
        };addMouseListener(mouse);addMouseMotionListener(mouse);addMouseWheelListener(mouse);
    }
    void setMachine(Automata.Machine machine){
        this.machine=machine;points.clear();fit=true;
        if(machine!=null){
            Map<Integer,Integer> rank=new HashMap<>();Deque<Integer> queue=new ArrayDeque<>();rank.put(machine.start(),0);queue.add(machine.start());
            Map<Integer,List<Integer>> adj=new HashMap<>();machine.edges().forEach(e->adj.computeIfAbsent(e.from(),k->new ArrayList<>()).add(e.to()));
            while(!queue.isEmpty()){int s=queue.removeFirst();for(int t:adj.getOrDefault(s,List.of()))if(!rank.containsKey(t)){rank.put(t,rank.get(s)+1);queue.add(t);}}
            Map<Integer,Integer> rows=new HashMap<>();for(int s=0;s<machine.count();s++){int col=rank.getOrDefault(s,0),row=rows.getOrDefault(col,0);rows.put(col,row+1);points.put(s,new Point2D.Double(col*180+60,row*105+65));}
        }
        repaint();
    }
    @Override public String getToolTipText(MouseEvent e){
        if(machine==null)return null;double x=(e.getX()-tx)/scale,y=(e.getY()-ty)/scale;
        for(var p:points.entrySet())if(p.getValue().distance(x,y)<28)return "状态 "+p.getKey()+" = "+machine.names().get(p.getKey());return "滚轮缩放 · 拖动平移 · 双击适应窗口";
    }
    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        if(machine==null){g.setFont(Ui.BODY);g.setColor(Ui.INK);g.drawString("输入表达式或文法，计算后查看状态图",25,40);g.dispose();return;}
        double maxX=points.values().stream().mapToDouble(p->p.x).max().orElse(100)+100,maxY=points.values().stream().mapToDouble(p->p.y).max().orElse(100)+70;
        if(fit){scale=Math.min(1.2,Math.min(Math.max(100,getWidth()-50)/maxX,Math.max(100,getHeight()-65)/maxY));tx=(getWidth()-maxX*scale)/2+15;ty=20;}
        g.translate(tx,ty);g.scale(scale,scale);g.setStroke(new BasicStroke(1.5f));g.setFont(Ui.CODE.deriveFont(13f));
        Map<String,List<String>> labels=new LinkedHashMap<>();Map<String,Automata.Edge> pairs=new LinkedHashMap<>();
        for(var e:machine.edges()){String k=e.from()+":"+e.to();labels.computeIfAbsent(k,x->new ArrayList<>()).add(e.display());pairs.put(k,e);}
        for(var entry:pairs.entrySet()){
            var e=entry.getValue();Point2D.Double a=points.get(e.from()),b=points.get(e.to());g.setColor(new Color(0x7D91AD));String label=String.join(",",labels.get(entry.getKey()));
            if(a.equals(b)){g.draw(new Arc2D.Double(a.x-24,a.y-66,48,58,-30,250,Arc2D.OPEN));arrow(g,a.x+24,a.y-17,Math.PI/2);text(g,label,a.x,a.y-72);}
            else{
                double angle=Math.atan2(b.y-a.y,b.x-a.x);double sx=a.x+27*Math.cos(angle),sy=a.y+27*Math.sin(angle),ex=b.x-29*Math.cos(angle),ey=b.y-29*Math.sin(angle);
                double bend=e.to()<e.from()?35:-12,dx=ex-sx,dy=ey-sy,len=Math.max(1,Math.hypot(dx,dy));double cx=(sx+ex)/2-dy/len*bend,cy=(sy+ey)/2+dx/len*bend;
                g.draw(new QuadCurve2D.Double(sx,sy,cx,cy,ex,ey));arrow(g,ex,ey,Math.atan2(ey-cy,ex-cx));text(g,label,(sx+2*cx+ex)/4,(sy+2*cy+ey)/4-7);
            }
        }
        for(int s=0;s<machine.count();s++){
            var p=points.get(s);g.setColor(machine.finals().contains(s)?new Color(0xE4F5EF):new Color(0xEFF4FF));g.fill(new Ellipse2D.Double(p.x-26,p.y-26,52,52));g.setColor(machine.finals().contains(s)?Ui.TEAL:Ui.BLUE);g.draw(new Ellipse2D.Double(p.x-26,p.y-26,52,52));
            if(machine.finals().contains(s))g.draw(new Ellipse2D.Double(p.x-21,p.y-21,42,42));g.setColor(Ui.INK);text(g,"q"+s,p.x,p.y+5);
            if(s==machine.start()){g.draw(new Line2D.Double(p.x-58,p.y,p.x-29,p.y));arrow(g,p.x-29,p.y,0);}
        }
        g.dispose();
    }
    private static void text(Graphics2D g,String s,double x,double y){if(s.length()>35)s=s.substring(0,32)+"…";int w=g.getFontMetrics().stringWidth(s);g.drawString(s,(float)(x-w/2.0),(float)y);}
    private static void arrow(Graphics2D g,double x,double y,double angle){Path2D p=new Path2D.Double();p.moveTo(x,y);p.lineTo(x-9*Math.cos(angle-.45),y-9*Math.sin(angle-.45));p.lineTo(x-9*Math.cos(angle+.45),y-9*Math.sin(angle+.45));p.closePath();g.fill(p);}
}
