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
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.util.Arrays;
import java.util.List;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import camidion.chordhelper.ButtonIcon;

/**
 * MIDI端子（{@link Transmitter}と{@link Receiver}）のリストビューです。
 * リストの要素をドラッグ＆ドロップすることで、
 * 2つのMIDI端子を仮想的なMIDIケーブルで接続することができます。
 */
public class MidiTransceiverListView extends JList<AutoCloseable> {

	public static final Icon MIDI_CONNECTER_ICON = new ButtonIcon(ButtonIcon.MIDI_CONNECTOR_ICON);
	/**
	 * リストに登録されている仮想MIDI端子の描画ツール
	 */
	private static class CellRenderer extends JLabel implements ListCellRenderer<AutoCloseable> {
		public Component getListCellRendererComponent(
				JList<? extends AutoCloseable> list, AutoCloseable value, int index,
				boolean isSelected, boolean cellHasFocus)
		{
			String text;
			if( value == null ) text = null;
			else if( value instanceof Receiver ) text = "Rx";
			else if( value instanceof Transmitter ) text = "Tx";
			else text = value.toString();
			setText(text);
			setIcon(MIDI_CONNECTER_ICON);
			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			} else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}
			setEnabled(list.isEnabled());
			setFont(list.getFont());
			setOpaque(true);
			return this;
		}
	}

	private static final DataFlavor transmitterFlavor = new DataFlavor(Transmitter.class, "Transmitter");
	private static final DataFlavor receiverFlavor = new DataFlavor(Receiver.class, "Receiver");
	/**
	 * ドラッグ対象を表すクラス
	 */
	private static class DraggingObject implements Transferable {
		private static final List<DataFlavor> flavors = Arrays.asList(transmitterFlavor, receiverFlavor);
		private AutoCloseable trx;
		@Override
		public Object getTransferData(DataFlavor flavor) {
			return flavor.getRepresentationClass().isInstance(trx) ? trx : null;
		}
		@Override
		public DataFlavor[] getTransferDataFlavors() { return (DataFlavor[]) flavors.toArray(); }
		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) { return flavors.contains(flavor); }
	};
	private static DraggingObject draggingObject = new DraggingObject();

	/**
	 * 現在ドラッグされている{@link Transmitter}または{@link Receiver}を返します。
	 * ドラッグ中でなければnullを返します。
	 */
	public AutoCloseable getDraggingTransceiver() { return draggingObject.trx; }

	private MidiCablePane cablePane;

	private DragSourceListener dragSourceListener = new DragSourceAdapter() {
		@Override
		public void dragDropEnd(DragSourceDropEvent dsde) {
			if( draggingObject.trx instanceof Transmitter && ! dsde.getDropSuccess() ) {
				getModel().closeTransmitter((Transmitter)draggingObject.trx);
			}
			draggingObject.trx = null;
			cablePane.dragDropEnd();
		}
	};
	/**
	 * 仮想MIDI端子リストビューを生成します。
	 * @param model このビューから参照されるデータモデル
	 * @param cablePane MIDIケーブル描画面
	 */
	public MidiTransceiverListView(MidiTransceiverListModel model, MidiCablePane cablePane) {
		super(model);
		this.cablePane = cablePane;
		setCellRenderer(new CellRenderer());
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setLayoutOrientation(JList.HORIZONTAL_WRAP);
		setVisibleRowCount(0);
		DragSource dragSource = new DragSource();
		DragGestureListener dgl = new DragGestureListener() {
			@Override
			public void dragGestureRecognized(DragGestureEvent dge) {
				if( (dge.getDragAction() & DnDConstants.ACTION_COPY_OR_MOVE) == 0 ) return;
				MidiTransceiverListModel m = getModel();
				AutoCloseable source = m.getElementAt(locationToIndex(dge.getDragOrigin()));
				if( source instanceof MidiTransceiverListModel.NewTransmitter ) {
					draggingObject.trx = m.openTransmitter();
				} else if( source instanceof Transmitter || source instanceof Receiver ) {
					draggingObject.trx = source;
				} else {
					return;
				}
				dge.startDrag(DragSource.DefaultLinkDrop, draggingObject, dragSourceListener);
			}
		};
		dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_COPY_OR_MOVE, dgl);
		dragSource.addDragSourceMotionListener(cablePane);
		DropTargetListener dtl = new DropTargetAdapter() {
			@Override
			public void dragEnter(DropTargetDragEvent event) {
				if( event.isDataFlavorSupported(transmitterFlavor) || event.isDataFlavorSupported(receiverFlavor) ) {
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
					MidiTransceiverListModel m = getModel();
					AutoCloseable destination = m.getElementAt(locationToIndex(event.getLocation()));
					Transferable t = event.getTransferable();
					if( t.isDataFlavorSupported(transmitterFlavor) || t.isDataFlavorSupported(receiverFlavor) ) {
						Object source;
						if( (source = t.getTransferData(transmitterFlavor)) != null ) {
							if( destination instanceof Receiver ) {
								((Transmitter)source).setReceiver((Receiver)destination);
								event.dropComplete(true);
								return;
							}
						} else if( (source = t.getTransferData(receiverFlavor)) != null ) {
							if( destination instanceof Transmitter ) {
								Transmitter tx = (Transmitter)destination;
								if( tx instanceof MidiTransceiverListModel.NewTransmitter ) {
									tx = m.openTransmitter();
								}
								tx.setReceiver((Receiver)source);
								event.dropComplete(true);
								return;
							}
						}
					}
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
	public MidiTransceiverListModel getModel() { return (MidiTransceiverListModel)super.getModel(); }
	/**
	 * 引数で指定された{@link Transmitter}または{@link Receiver}のセル範囲を示す、
	 * リストの座標系内の境界の矩形を返します。対応するセルがない場合はnullを返します。
	 * @param transceiver {@link Transmitter}または{@link Receiver}
	 * @return セル範囲を示す境界の矩形、またはnull
	 */
	public Rectangle getCellBounds(AutoCloseable transceiver) {
		int index = getModel().indexOf(transceiver);
		return getCellBounds(index,index);
	}
}
