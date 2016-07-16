package camidion.chordhelper.mididevice;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
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
import javax.swing.TransferHandler;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/**
 * MIDI ケーブル描画面
 */
public class MidiCablePane extends JComponent {
	private Point draggingLocation;
	private Object draggingSource;
	private Object draggingDestination;
	/**
	 * ドラッグ中の{@link Transmitter}または{@link Receiver}を
	 * {@link Transferable}インターフェースで参照できるようにするクラス
	 */
	public class DraggingTransceiver<E> implements Transferable {
		private DataFlavor[] draggingFlavors;
		public DraggingTransceiver(AbstractTransceiverListView<E> listView) {
			draggingFlavors = listView.getElementDataFlavorArray();
			draggingSource = listView.getSelectedValue();
			repaint();
		}
		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
			if( ! isDataFlavorSupported(flavor) ) throw new UnsupportedFlavorException(flavor);
			return draggingSource;
		}
		@Override
		public DataFlavor[] getTransferDataFlavors() { return draggingFlavors; }
		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return draggingFlavors[0] == flavor;
		}
	}
	/**
	 * ドラッグ＆ドロップ中のケーブル描画情報を
	 * {@link MidiCablePane}クラスのインスタンスに提供するための{@link TransferHandler}
	 */
	public abstract class TransceiverTransferHandler<E> extends TransferHandler {
		private AbstractTransceiverListView<E> listView;
		private DataFlavor destinationDataFlavor;
		public TransceiverTransferHandler(AbstractTransceiverListView<E> listView, DataFlavor destinationDataFlavor) {
			this.listView = listView;
			this.destinationDataFlavor = destinationDataFlavor;
		}
		@Override
		public int getSourceActions(JComponent compo) { return COPY_OR_MOVE; }
		@Override
		protected Transferable createTransferable(JComponent compo) {
			return new DraggingTransceiver<E>(listView);
		}
		@Override
		protected void exportDone(JComponent source, Transferable data, int action) {
			draggingSource = null;
			draggingLocation = null;
			draggingDestination = null;
			repaint();
		}
		@Override
		public boolean canImport(TransferSupport support) {
			if( ! support.isDrop() ) return false;
			if( ! support.isDataFlavorSupported(destinationDataFlavor) ) {
				draggingDestination = null;
				repaint();
				return false;
			}
			draggingDestination = listView.getElementAt(support.getDropLocation().getDropPoint());
			repaint();
			return true;
		}
		public DataFlavor getDestinationDataFlavor() {
			return destinationDataFlavor;
		}
	}
	/**
	 * ドラッグ中、ドロップできるエリアを外れたとき、この描画面に通知します。
	 */
	public void draggedOutOfDestination() {
		draggingDestination = null;
		repaint();
	}
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
			for( Receiver rx : rxList ) rxToColor.remove(rx);
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
		DragSource.getDefaultDragSource().addDragSourceMotionListener(new DragSourceMotionListener() {
			@Override
		    public void dragMouseMoved(DragSourceDragEvent dsde) {
				// OSのスクリーン座標系から、このケーブル画面の座標系に変換する
		    	Point origin = getLocationOnScreen();
		    	(draggingLocation = dsde.getLocation()).translate(-origin.x, -origin.y);
		    	repaint();
		    }
		});
	}

	private static final Stroke CABLE_STROKE = new BasicStroke(
			3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private static final float dash[] = {1.0f, 5.0f};
	private static final Stroke VIRTUAL_CABLE_STROKE = new BasicStroke(
			3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, dash, 0.0f);
	private static final List<Color> CABLE_COLORS = Arrays.asList(
		new Color(255, 0, 0,144),
		new Color(0, 255, 0,144),
		new Color(0, 0, 255,144),
		new Color(191,191,0,144),
		new Color(0,191,191,144),
		new Color(191,0,191,144)
	);
	private static final Color NEW_CABLE_COLOR = new Color(0, 0, 0, 144);
	private Hashtable<Receiver,Color> rxToColor = new Hashtable<>();
	private Color colorOf(Receiver rx) {
		// そのレシーバに割り当て済みの色を探す
		Color myColor = rxToColor.get(rx);
		if( myColor != null ) return myColor;
		// なければ、未使用の色を探して割り当てる
		for( Color virginColor : CABLE_COLORS ) {
			if( rxToColor.containsValue(virginColor) ) continue;
			rxToColor.put(rx, virginColor);
			return virginColor;
		}
		// 全色使われていたら、重複してでもランダムに割り当てる
		Color randomColor = CABLE_COLORS.get((int)( Math.random() * CABLE_COLORS.size() ));
		rxToColor.put(rx, randomColor);
		return randomColor;
	}
	@Override
	public void paint(Graphics g) {
		super.paint(g);
		Graphics2D g2 = (Graphics2D)g;
		JInternalFrame[] frames = desktopPane.getAllFramesInLayer(JLayeredPane.DEFAULT_LAYER);
		//
		// 各フレームをスキャン
		for( JInternalFrame frame : frames ) {
			if( ! (frame instanceof MidiDeviceFrame) ) continue;
			MidiDeviceFrame fromFrame = (MidiDeviceFrame)frame;
			MidiDeviceModel fromDeviceModel = fromFrame.getMidiDeviceModel();
			//
			// Transmitterをスキャン
			TransmitterListModel txListModel = fromDeviceModel.getTransmitterListModel();
			int ntx = txListModel == null ? 0 : txListModel.getSize();
			for( int index=0 ; index < ntx; index++ ) {
				Transmitter tx = txListModel.getElementAt(index);
				Receiver rx = tx.getReceiver();
				if( rx == null && (draggingLocation == null || ! tx.equals(draggingSource)) ) {
					// このTransmitterから描画すべきケーブルはない
					continue;
				}
				// Transmitterの表示場所を特定
				Rectangle txBounds = fromFrame.getBoundsOf(tx);
				if( txBounds == null ) continue;
				int r = (txBounds.height - 5) / 2;
				txBounds.translate(r+4, r+4);
				//
				// TransmitterからReceiverへのケーブルを描画
				if( rx != null ) {
					// Receiverの表示場所を探す
					for( JInternalFrame toFrame : frames ) {
						if( ! (toFrame instanceof MidiDeviceFrame) ) continue;
						Rectangle rxBounds = ((MidiDeviceFrame)toFrame).getBoundsOf(rx);
						if( rxBounds == null ) continue;
						r = (rxBounds.height - 5) / 2;
						rxBounds.translate(r+4, r+4);
						//
						// 場所が判明したら描画
						g2.setStroke(tx.equals(draggingSource) ? VIRTUAL_CABLE_STROKE : CABLE_STROKE);
						g2.setColor(colorOf(rx));
						g2.drawLine(txBounds.x, txBounds.y, rxBounds.x, rxBounds.y);
						break;
					}
				}
				// Transmitterからドラッグ中のケーブルを描画
				if( draggingLocation != null && tx.equals(draggingSource) ) {
					if( draggingDestination instanceof Receiver ) {
						g2.setStroke(CABLE_STROKE);
						g2.setColor(colorOf((Receiver)draggingDestination));
					} else {
						g2.setStroke(VIRTUAL_CABLE_STROKE);
						g2.setColor(NEW_CABLE_COLOR);
					}
					g2.drawLine(txBounds.x, txBounds.y, draggingLocation.x, draggingLocation.y);
				}
			}
			// Receiverからドラッグ中のケーブルを描画
			if( draggingLocation != null && fromDeviceModel.getMidiDevice().getReceivers().contains(draggingSource) ) {
				Receiver rx = (Receiver)draggingSource;
				Rectangle rxBounds = fromFrame.getBoundsOf(rx);
				if( rxBounds != null ) {
					int r = (rxBounds.height - 5) / 2;
					rxBounds.translate(r+4, r+4);
					if( draggingDestination instanceof Transmitter ) {
						g2.setStroke(CABLE_STROKE);
					} else {
						g2.setStroke(VIRTUAL_CABLE_STROKE);
					}
					g2.setColor(colorOf(rx));
					g2.drawLine(rxBounds.x, rxBounds.y, draggingLocation.x, draggingLocation.y);
				}
			}
		}
	}
}
