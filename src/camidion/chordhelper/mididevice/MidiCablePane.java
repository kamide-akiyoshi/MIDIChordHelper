package camidion.chordhelper.mididevice;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.Hashtable;
import java.util.List;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JLayeredPane;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/**
 * MIDI ケーブル描画面
 */
public class MidiCablePane extends JComponent
	implements ListDataListener, ComponentListener, InternalFrameListener
{
	private JDesktopPane desktopPane;
	//private JTree tree;
	public MidiCablePane(JDesktopPane desktopPane) {
		this.desktopPane = desktopPane;
		setOpaque(false);
		setVisible(true);
	}
	//
	// MidiDeviceFrame の開閉を検出
	public void internalFrameActivated(InternalFrameEvent e) {}
	public void internalFrameClosed(InternalFrameEvent e) { repaint(); }
	public void internalFrameClosing(InternalFrameEvent e) {
		JInternalFrame frame = e.getInternalFrame();
		if( ! (frame instanceof MidiDeviceFrame) )
			return;
		MidiDeviceFrame devFrame = (MidiDeviceFrame)frame;
		MidiConnecterListModel devModel = devFrame.listView.getModel();
		if( ! devModel.rxSupported() )
			return;
		colorMap.remove(devModel.getMidiDevice().getReceivers().get(0));
		repaint();
	}
	public void internalFrameDeactivated(InternalFrameEvent e) { repaint(); }
	public void internalFrameDeiconified(InternalFrameEvent e) {}
	public void internalFrameIconified(InternalFrameEvent e) {}
	public void internalFrameOpened(InternalFrameEvent e) {}
	//
	// ウィンドウオペレーションの検出
	public void componentHidden(ComponentEvent e) {}
	public void componentMoved(ComponentEvent e) { repaint(); }
	public void componentResized(ComponentEvent e) { repaint(); }
	public void componentShown(ComponentEvent e) {}
	//
	// MidiConnecterListModel における Transmitter リストの更新を検出
	public void contentsChanged(ListDataEvent e) { repaint(); }
	public void intervalAdded(ListDataEvent e) { repaint(); }
	public void intervalRemoved(ListDataEvent e) { repaint(); }
	//
	// ケーブル描画用
	private static final Stroke CABLE_STROKE = new BasicStroke(
		3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND
	);
	private static final Color[] CABLE_COLORS = {
		new Color(255, 0, 0,144),
		new Color(0, 255, 0,144),
		new Color(0, 0, 255,144),
		new Color(191,191,0,144),
		new Color(0,191,191,144),
		new Color(191,0,191,144),
	};
	private int nextColorIndex = 0;
	private Hashtable<Receiver,Color> colorMap = new Hashtable<>();
	@Override
	public void paint(Graphics g) {
		super.paint(g);
		Graphics2D g2 = (Graphics2D)g;
		g2.setStroke(CABLE_STROKE);
		JInternalFrame[] frames =
			desktopPane.getAllFramesInLayer(JLayeredPane.DEFAULT_LAYER);
		for( JInternalFrame frame : frames ) {
			if( ! (frame instanceof MidiDeviceFrame) )
				continue;
			MidiDeviceFrame txDeviceFrame = (MidiDeviceFrame)frame;
			List<Transmitter> txList = txDeviceFrame.listView.getModel().getMidiDevice().getTransmitters();
			for( Transmitter tx : txList ) {
				//
				// 送信端子から接続されている受信端子の存在を確認
				Receiver rx = tx.getReceiver();
				if( rx == null )
					continue;
				//
				// 送信端子の矩形を特定
				Rectangle txRect = txDeviceFrame.getListCellBounds(tx);
				if( txRect == null )
					continue;
				//
				// 受信端子のあるMIDIデバイスを探す
				Rectangle rxRect = null;
				for( JInternalFrame anotherFrame : frames ) {
					if( ! (anotherFrame instanceof MidiDeviceFrame) )
						continue;
					//
					// 受信端子の矩形を探す
					MidiDeviceFrame rxDeviceFrame = (MidiDeviceFrame)anotherFrame;
					if((rxRect = rxDeviceFrame.getListCellBounds(rx)) == null)
						continue;
					rxRect.translate(rxDeviceFrame.getX(), rxDeviceFrame.getY());
					break;
				}
				if( rxRect == null )
					continue;
				txRect.translate(txDeviceFrame.getX(), txDeviceFrame.getY());
				//
				// 色を探す
				Color color = colorMap.get(rx);
				if( color == null ) {
					colorMap.put(rx, color=CABLE_COLORS[nextColorIndex++]);
					if( nextColorIndex >= CABLE_COLORS.length )
						nextColorIndex = 0;
				}
				g2.setColor(color);
				//
				// Tx 始点
				int fromX = txRect.x;
				int fromY = txRect.y + 2;
				int d = txRect.height - 5;
				g2.fillOval(fromX, fromY, d, d);
				//
				// Tx → Rx 線
				int r = d / 2;
				fromX += r;
				fromY += r;
				d = rxRect.height - 5;
				r = d / 2;
				int toX = rxRect.x + r;
				int toY = rxRect.y + r + 2;
				g2.drawLine(fromX, fromY, toX, toY);
			}
		}
	}
}