package camidion.chordhelper.mididevice;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DragSourceMotionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.JLayeredPane;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/**
 * MIDI ケーブル描画面
 */
public class MidiCablePane extends JComponent {
	static DraggingTransceiver dragging = new DraggingTransceiver();
	private Point draggingPoint;
	/**
	 * ドラッグしている最中に再描画するためのソースモーションリスナー
	 */
	public final DragSourceMotionListener dragSourceMotionListener = new DragSourceMotionListener() {
		@Override
		public void dragMouseMoved(DragSourceDragEvent dsde) {
			Point origin = getLocationOnScreen();
			draggingPoint = dsde.getLocation();
			draggingPoint.translate(-origin.x, -origin.y);
			repaint();
		}

	};
	/**
	 * ドラッグ終了時に再描画するためのソースリスナー
	 */
	public final DragSourceListener dragSourceListener = new DragSourceAdapter() {
		@Override
		public void dragDropEnd(DragSourceDropEvent dsde) {
			dragging.setData(null);
			draggingPoint = null;
			repaint();
		}
	};
	/**
	 * {@link MidiDeviceFrame} が移動または変形したときにケーブルを再描画するためのリスナー
	 */
	public final ComponentListener midiDeviceFrameComponentListener = new ComponentAdapter() {
		@Override
		public void componentMoved(ComponentEvent e) { repaint(); }
		@Override
		public void componentResized(ComponentEvent e) { repaint(); }
	};
	/**
	 * {@link MidiDeviceFrame} が閉じたときにケーブルを再描画するためのリスナー
	 */
	public final InternalFrameListener midiDeviceFrameListener = new InternalFrameAdapter() {
		@Override
		public void internalFrameClosed(InternalFrameEvent e) { repaint(); }
		@Override
		public void internalFrameDeactivated(InternalFrameEvent e) { repaint(); }
		@Override
		public void internalFrameClosing(InternalFrameEvent e) {
			JInternalFrame frame = e.getInternalFrame();
			if( ! (frame instanceof MidiDeviceFrame) ) return;
			MidiDeviceFrame f = (MidiDeviceFrame)frame;
			MidiDeviceModel m = f.getMidiDeviceModel();
			List<Receiver> rxList = m.getMidiDevice().getReceivers();
			for( Receiver rx : rxList ) colorMap.remove(rx);
			repaint();
		}
	};
	/**
	 * {@link MidiDeviceModel} における {@link Transmitter}
	 * の増減や状態変更があった場合にケーブルを再描画するためのリスナー
	 */
	public final ListDataListener midiConnecterListDataListener = new ListDataListener() {
		@Override
		public void contentsChanged(ListDataEvent e) { repaint(); }
		@Override
		public void intervalAdded(ListDataEvent e) { repaint(); }
		@Override
		public void intervalRemoved(ListDataEvent e) { repaint(); }
	};

	private MidiOpenedDevicesView desktopPane;
	public MidiCablePane(MidiOpenedDevicesView desktopPane) {
		this.desktopPane = desktopPane;
		setOpaque(false);
		setVisible(true);
	}

	private static final Stroke CABLE_STROKE = new BasicStroke(
			3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private static final List<Color> CABLE_COLORS = Arrays.asList(
		new Color(255, 0, 0,144),
		new Color(0, 255, 0,144),
		new Color(0, 0, 255,144),
		new Color(191,191,0,144),
		new Color(0,191,191,144),
		new Color(191,0,191,144)
	);
	private static final Color ADDING_CABLE_COLOR = new Color(0, 0, 0, 144);
	private static final Color REMOVING_CABLE_COLOR = new Color(128, 128, 128, 144);
	private Hashtable<Receiver,Color> colorMap = new Hashtable<>();
	private Color colorOf(Receiver rx) {
		Color color = colorMap.get(rx);
		if( color == null ) {
			for( Color c : CABLE_COLORS ) {
				if( ! colorMap.containsValue(c) ) {
					colorMap.put(rx, c);
					return c;
				}
			}
			color = CABLE_COLORS.get((int)( Math.random() * CABLE_COLORS.size() ));
			colorMap.put(rx, color);
		}
		return color;
	}
	@Override
	public void paint(Graphics g) {
		super.paint(g);
		Graphics2D g2 = (Graphics2D)g;
		g2.setStroke(CABLE_STROKE);
		JInternalFrame[] frames = desktopPane.getAllFramesInLayer(JLayeredPane.DEFAULT_LAYER);
		for( JInternalFrame frame : frames ) {
			if( ! (frame instanceof MidiDeviceFrame) ) continue;
			MidiDeviceFrame fromFrame = (MidiDeviceFrame)frame;
			MidiDeviceModel fromDeviceModel = fromFrame.getMidiDeviceModel();
			//
			// Receiverからドラッグされている線を描画
			if( draggingPoint != null && fromDeviceModel.getMidiDevice().getReceivers().contains(dragging.getData()) ) {
				Receiver rx = (Receiver)dragging.getData();
				Rectangle rxBounds = fromFrame.getBoundsOf(rx);
				if( rxBounds == null ) continue;
				int r = (rxBounds.height - 5) / 2;
				rxBounds.translate(r+4, r+4);
				g2.setColor(colorOf(rx));
				g2.drawLine(rxBounds.x, rxBounds.y, draggingPoint.x, draggingPoint.y);
			}
			// Transmitterを全部スキャン
			TransmitterListModel txListModel = fromDeviceModel.getTransmitterListModel();
			int ntx = txListModel == null ? 0 : txListModel.getSize();
			for( int index=0 ; index < ntx; index++ ) {
				Transmitter tx = txListModel.getElementAt(index);
				//
				// Transmitterの場所を特定
				Rectangle txBounds = fromFrame.getBoundsOf(tx);
				if( txBounds == null ) continue;
				int r = (txBounds.height - 5) / 2;
				txBounds.translate(r+4, r+4);
				//
				// Transmitterに現在接続されているReceiverを把握
				Receiver rx = tx.getReceiver();
				if( draggingPoint != null && tx.equals(dragging.getData()) ) {
					//
					// Transmitterからドラッグされている線を描画
					g2.setColor(rx == null ? ADDING_CABLE_COLOR : colorOf(rx));
					g2.drawLine(txBounds.x, txBounds.y, draggingPoint.x, draggingPoint.y);
				}
				if( rx == null ) continue;
				for( JInternalFrame toFrame : frames ) {
					if( ! (toFrame instanceof MidiDeviceFrame) ) continue;
					//
					// Receiverの場所を特定
					Rectangle rxBounds = ((MidiDeviceFrame)toFrame).getBoundsOf(rx);
					if( rxBounds == null ) continue;
					r = (rxBounds.height - 5) / 2;
					rxBounds.translate(r+4, r+4);
					//
					// Transmitter⇔Receiver間の線を描画
					g2.setColor(tx.equals(dragging.getData()) ? REMOVING_CABLE_COLOR : colorOf(rx));
					g2.drawLine(txBounds.x, txBounds.y, rxBounds.x, rxBounds.y);
					break;
				}
			}
		}
	}
}
