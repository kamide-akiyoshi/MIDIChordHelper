package camidion.chordhelper.mididevice;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.util.Arrays;
import java.util.List;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import camidion.chordhelper.mididevice.MidiDeviceModel.NewTransmitter;

/**
 * MIDIトランスミッタ（{@link Transmitter}）のリストビューです。
 * トランスミッタをこのビューからドラッグし、
 * {@link MidiReceiverListView} のレシーバにドロップして接続できます。
 */
public class MidiTransmitterListView extends JList<Transmitter> {
	/**
	 * トランスミッタを描画するクラス
	 */
	private static class CellRenderer extends JLabel implements ListCellRenderer<Transmitter> {
		public Component getListCellRendererComponent(JList<? extends Transmitter> list,
				Transmitter value, int index, boolean isSelected, boolean cellHasFocus)
		{
			setEnabled(list.isEnabled());
			setFont(list.getFont());
			setOpaque(true);
			setIcon(MidiDeviceDialog.MIDI_CONNECTER_ICON);
			if( value == null ) {
				setToolTipText(null);
			} else {
				if( value instanceof NewTransmitter ) {
					setToolTipText("ドラッグ＆ドロップしてRxに接続");
				} else {
					setToolTipText("ドラッグ＆ドロップして切断、またはRx切替");
				}
			}
			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			} else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}
			return this;
		}
	}
	/**
	 * ドラッグ対象トランスミッタを表すクラス
	 */
	private static class DraggingTransmitter implements Transferable {
		private static final List<DataFlavor> flavors = Arrays.asList(MidiDeviceDialog.transmitterFlavor);
		private Transmitter tx;
		@Override
		public Object getTransferData(DataFlavor flavor) {
			return flavor.getRepresentationClass().isInstance(tx) ? tx : null;
		}
		@Override
		public DataFlavor[] getTransferDataFlavors() { return (DataFlavor[]) flavors.toArray(); }
		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) { return flavors.contains(flavor); }
	};
	private static DraggingTransmitter draggingTransmitter = new DraggingTransmitter();

	/**
	 * 現在ドラッグされている{@link Transmitter}を返します。
	 * ドラッグ中でなければnullを返します。
	 */
	public Transmitter getDraggingTransmitter() { return draggingTransmitter.tx; }

	private MidiCablePane cablePane;

	/**
	 * 仮想MIDI端子リストビューを生成します。
	 * @param model このビューから参照されるデータモデル
	 * @param cablePane MIDIケーブル描画面
	 */
	public MidiTransmitterListView(MidiDeviceModel.TransmitterListModel model, MidiCablePane cablePane) {
		super(model);
		this.cablePane = cablePane;
		setCellRenderer(new CellRenderer());
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setLayoutOrientation(JList.HORIZONTAL_WRAP);
		setVisibleRowCount(0);
		//
		// トランスミッタのドラッグを受け付ける
		DragSource dragSource = new DragSource();
		DragGestureListener dgl = new DragGestureListener() {
			@Override
			public void dragGestureRecognized(DragGestureEvent event) {
				if( (event.getDragAction() & DnDConstants.ACTION_COPY_OR_MOVE) == 0 ) return;
				draggingTransmitter.tx = getModel().getElementAt(locationToIndex(event.getDragOrigin()));
				if( draggingTransmitter.tx instanceof NewTransmitter ) {
					draggingTransmitter.tx = getModel().openTransmitter();
				}
				event.startDrag(DragSource.DefaultLinkDrop, draggingTransmitter, new DragSourceAdapter() {
					@Override
					public void dragDropEnd(DragSourceDropEvent event) {
						if( ! event.getDropSuccess() ) getModel().closeTransmitter((Transmitter)draggingTransmitter.tx);
						draggingTransmitter.tx = null;
						MidiTransmitterListView.this.cablePane.dragDropEnd();
					}
				});
			}
		};
		dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY_OR_MOVE, dgl);
		dragSource.addDragSourceMotionListener(cablePane);
		//
		// レシーバのドロップを受け付ける
		DropTargetListener dtl = new DropTargetAdapter() {
			@Override
			public void dragEnter(DropTargetDragEvent event) {
				if( event.isDataFlavorSupported(MidiDeviceDialog.receiverFlavor) ) {
					event.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
				}
			}
			@Override
			public void drop(DropTargetDropEvent event) {
				event.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
				try {
					int maskedBits = event.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE;
					if( maskedBits == 0 ) {
						event.dropComplete(false);
						return;
					}
					Transferable t = event.getTransferable();
					if( ! t.isDataFlavorSupported(MidiDeviceDialog.receiverFlavor) ) {
						event.dropComplete(false);
						return;
					}
					Object source = t.getTransferData(MidiDeviceDialog.receiverFlavor);
					if( ! (source instanceof Receiver) ) {
						event.dropComplete(false);
						return;
					}
					Transmitter destTx = getModel().getElementAt(locationToIndex(event.getLocation()));
					if( destTx instanceof NewTransmitter ) destTx = getModel().openTransmitter();
					destTx.setReceiver((Receiver)source);
					event.dropComplete(true);
					return;
				}
				catch (Exception ex) {
					ex.printStackTrace();
				}
				event.dropComplete(false);
			}
		};
		new DropTarget( this, DnDConstants.ACTION_COPY_OR_MOVE, dtl, true );
	}
	@Override
	public MidiDeviceModel.TransmitterListModel getModel() {
		return (MidiDeviceModel.TransmitterListModel) super.getModel();
	}
	/**
	 * 引数で指定された{@link Transmitter}のセル範囲を示す、
	 * リストの座標系内の境界の矩形を返します。対応するセルがない場合はnullを返します。
	 * @return セル範囲を示す境界の矩形、またはnull
	 */
	public Rectangle getCellBounds(Transmitter tx) {
		int index = getModel().indexOf(tx);
		return getCellBounds(index,index);
	}
}
