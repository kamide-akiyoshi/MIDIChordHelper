package camidion.chordhelper.mididevice;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceMotionListener;
import java.awt.event.ComponentAdapter;
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
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/**
 * MIDI ケーブル描画面
 */
public class MidiCablePane extends JComponent {
	private Point draggingPoint;
	/**
	 * {@link MidiConnecterListModel} の {@link Transmitter}
	 * をドラッグしている最中に再描画するためのリスナー
	 */
	public final DragSourceMotionListener midiConnecterMotionListener = new DragSourceMotionListener() {
		@Override
		public void dragMouseMoved(DragSourceDragEvent dsde) {
			Point origin = MidiCablePane.this.getLocationOnScreen();
			draggingPoint = dsde.getLocation();
			draggingPoint.translate(-origin.x, -origin.y);
			repaint();
		}
	};
	/**
	 * ドラッグ＆ドロップの終了時にこのメソッドを呼び出します。
	 */
	public void dragDropEnd() { draggingPoint = null; repaint(); }
	/**
	 * {@link MidiDeviceFrame} の移動や変形を監視して再描画するためのリスナー
	 */
	public final ComponentListener midiDeviceFrameComponentListener = new ComponentAdapter() {
		@Override
		public void componentMoved(ComponentEvent e) { repaint(); }
		@Override
		public void componentResized(ComponentEvent e) { repaint(); }
	};
	/**
	 * {@link MidiDeviceFrame} が閉じたタイミングで再描画するためのリスナー
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
			MidiDeviceFrame devFrame = (MidiDeviceFrame)frame;
			MidiConnecterListModel devModel = devFrame.getMidiConnecterListView().getModel();
			if( devModel.rxSupported() ) {
				colorMap.remove(devModel.getMidiDevice().getReceivers().get(0));
			}
			repaint();
		}
	};
	/**
	 * {@link MidiConnecterListModel} における {@link Transmitter}
	 * の増減や状態変更があった場合に再描画するためのリスナー
	 */
	public final ListDataListener midiConnecterListDataListener = new ListDataListener() {
		@Override
		public void contentsChanged(ListDataEvent e) { repaint(); }
		@Override
		public void intervalAdded(ListDataEvent e) { repaint(); }
		@Override
		public void intervalRemoved(ListDataEvent e) { repaint(); }
	};

	private JDesktopPane desktopPane;
	public MidiCablePane(JDesktopPane desktopPane) {
		this.desktopPane = desktopPane;
		setOpaque(false);
		setVisible(true);
	}

	private static final Stroke CABLE_STROKE = new BasicStroke(
			3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
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
		JInternalFrame[] frames = desktopPane.getAllFramesInLayer(JLayeredPane.DEFAULT_LAYER);
		for( JInternalFrame frame : frames ) {
			if( ! (frame instanceof MidiDeviceFrame) ) continue;
			MidiDeviceFrame txDeviceFrame = (MidiDeviceFrame)frame;
			MidiConnecterListView txView = txDeviceFrame.getMidiConnecterListView();
			Transmitter draggingTx = txView.getDraggingTransmitter();
			List<Transmitter> txList = txView.getModel().getMidiDevice().getTransmitters();
			for( Transmitter tx : txList ) {
				//
				// 送信端子の場所を特定
				Rectangle txRect = txView.getCellBounds(tx);
				if( txRect == null ) continue;
				txRect.translate(
					txDeviceFrame.getRootPane().getX() +
					txDeviceFrame.getContentPane().getX() + txDeviceFrame.getX(),
					txDeviceFrame.getRootPane().getY() +
					txDeviceFrame.getContentPane().getY() + txDeviceFrame.getY()
				);
				int d = txRect.height - 5;
				int r = d / 2;
				int fromX = txRect.x + r + 4;
				int fromY = txRect.y + r + 4;
				//
				// ドラッグ中であれば、マウスカーソルのある所まで線を引く
				if( tx.equals(draggingTx) && draggingPoint != null ) {
					g2.setColor(Color.BLACK);
					g2.drawLine(fromX, fromY, draggingPoint.x, draggingPoint.y);
					continue;
				}
				// 受信端子の場所を特定
				Receiver rx = tx.getReceiver();
				if( rx == null ) continue;
				Rectangle rxRect = null;
				for( JInternalFrame anotherFrame : frames ) {
					if( ! (anotherFrame instanceof MidiDeviceFrame) ) continue;
					MidiDeviceFrame rxDeviceFrame = (MidiDeviceFrame)anotherFrame;
					rxRect = rxDeviceFrame.getMidiConnecterListView().getCellBounds(rx);
					if( rxRect == null ) continue;
					rxRect.translate(
						rxDeviceFrame.getRootPane().getX() +
						rxDeviceFrame.getContentPane().getX() + rxDeviceFrame.getX(),
						rxDeviceFrame.getRootPane().getY() +
						rxDeviceFrame.getContentPane().getY() + rxDeviceFrame.getY()
					);
					break;
				}
				if( rxRect == null ) continue;
				//
				// 受信端子まで線を引く
				Color color = colorMap.get(rx);
				if( color == null ) {
					colorMap.put(rx, color=CABLE_COLORS[nextColorIndex++]);
					if( nextColorIndex >= CABLE_COLORS.length ) nextColorIndex = 0;
				}
				g2.setColor(color);
				d = rxRect.height - 5;
				r = d / 2;
				int toX = rxRect.x + r + 4;
				int toY = rxRect.y + r + 4;
				g2.drawLine(fromX, fromY, toX, toY);
			}
		}
	}
}
