package camidion.chordhelper.midieditor;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.EventObject;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;

import camidion.chordhelper.ChordHelperApplet;
import camidion.chordhelper.mididevice.VirtualMidiDevice;

/**
 * MIDIイベントリストテーブルビュー（選択中のトラックの中身）
 */
public class MidiEventTable extends JTable {
	/**
	 * MIDIイベント入力ダイアログ（イベント入力とイベント送出で共用）
	 */
	private MidiEventDialog eventDialog;
	/**
	 * 操作音を鳴らすMIDI出力デバイス
	 */
	private VirtualMidiDevice outputMidiDevice;
	/**
	 * イベント選択リスナー
	 */
	private ListSelectionListener selectionListener;
	/**
	 * 新しいイベントリストテーブルを構築します。
	 * <p>データモデルとして一つのトラックのイベントリストを指定できます。
	 * トラックを切り替えたいときは {@link #setModel(TableModel)}
	 * でデータモデルを異なるトラックのものに切り替えます。
	 * </p>
	 *
	 * @param model トラック（イベントリスト）データモデル
	 * @param eventDialog MIDIイベントダイアログ
	 * @param outputMidiDevice 操作音出力先MIDIデバイス
	 */
	public MidiEventTable(TrackEventListTableModel model, MidiEventDialog eventDialog, VirtualMidiDevice outputMidiDevice) {
		super(model, null, model.getSelectionModel());
		this.outputMidiDevice = outputMidiDevice;
		this.eventDialog = eventDialog;
		titleLabel = new TitleLabel();
		Arrays.stream(TrackEventListTableModel.Column.values()).forEach(c->
			getColumnModel().getColumn(c.ordinal()).setPreferredWidth(c.preferredWidth)
		);
		pairNoteOnOffModel = new JToggleButton.ToggleButtonModel() {
			{
				addItemListener(e->eventDialog.midiMessageForm.durationForm.setEnabled(isSelected()));
				setSelected(true);
			}
		};
		eventCellEditor = new MidiEventCellEditor();
		setAutoCreateColumnsFromModel(false);
		selectionModel.addListSelectionListener(selectionListener = event->{
			if( event.getValueIsAdjusting() ) return;
			if( selectionModel.isSelectionEmpty() ) {
				queryPasteEventAction.setEnabled(false);
				copyEventAction.setEnabled(false);
				deleteEventAction.setEnabled(false);
				cutEventAction.setEnabled(false);
			}
			else {
				copyEventAction.setEnabled(true);
				deleteEventAction.setEnabled(true);
				cutEventAction.setEnabled(true);
				int minIndex = selectionModel.getMinSelectionIndex();
				MidiEvent midiEvent = model.getMidiEvent(minIndex);
				if( midiEvent != null ) {
					MidiMessage msg = midiEvent.getMessage();
					if( msg instanceof ShortMessage ) {
						ShortMessage sm = (ShortMessage)msg;
						int cmd = sm.getCommand();
						if( cmd == 0x80 || cmd == 0x90 || cmd == 0xA0 ) {
							// ノート番号を持つ場合、音を鳴らす。
							MidiChannel outMidiChannels[] = outputMidiDevice.getChannels();
							int ch = sm.getChannel();
							int note = sm.getData1();
							int vel = sm.getData2();
							outMidiChannels[ch].noteOn(note, vel);
							outMidiChannels[ch].noteOff(note, vel);
						}
					}
				}
				if( pairNoteOnOffModel.isSelected() ) {
					int maxIndex = selectionModel.getMaxSelectionIndex();
					int partnerIndex;
					for( int i=minIndex; i<=maxIndex; i++ ) {
						if( ! selectionModel.isSelectedIndex(i) ) continue;
						partnerIndex = model.getIndexOfPartnerFor(i);
						if( partnerIndex >= 0 && ! selectionModel.isSelectedIndex(partnerIndex) )
							selectionModel.addSelectionInterval(partnerIndex, partnerIndex);
					}
				}
			}
		});
	}
	/**
	 * このテーブルビューが表示するデータを提供するトラック（イベントリスト）データモデルを返します。
	 * @return トラック（イベントリスト）データモデル
	 */
	@Override
	public TrackEventListTableModel getModel() {
		return (TrackEventListTableModel) dataModel;
	}
	/**
	 * このテーブルビューが表示するデータを提供するトラック（イベントリスト）データモデルを設定します。
	 * @param model トラック（イベントリスト）データモデル
	 */
	public void setModel(TrackEventListTableModel model) {
		if( dataModel == model ) return;
		if( model == null ) {
			PlaylistTableModel playlist = getModel().getParent().getParent();
			model = playlist.emptyEventListTableModel;
			queryJumpEventAction.setEnabled(false);
			queryAddEventAction.setEnabled(false);

			queryPasteEventAction.setEnabled(false);
			copyEventAction.setEnabled(false);
			deleteEventAction.setEnabled(false);
			cutEventAction.setEnabled(false);
		}
		else {
			queryJumpEventAction.setEnabled(true);
			queryAddEventAction.setEnabled(true);
		}
		selectionModel.removeListSelectionListener(selectionListener);
		super.setModel(model);
		setSelectionModel(model.getSelectionModel());
		titleLabel.updateTrackNumber(model.getParent().getSelectionModel().getMinSelectionIndex());
		selectionModel.addListSelectionListener(selectionListener);
	}
	/**
	 * タイトルラベル
	 */
	TitleLabel titleLabel;
	/**
	 * 親テーブルの選択トラックの変更に反応する
	 * トラック番号つきタイトルラベル
	 */
	class TitleLabel extends JLabel {
		private static final String TITLE = "MIDI Events";
		private TitleLabel() { super(TITLE); }
		void updateTrackNumber(int index) {
			String text = TITLE;
			if( index >= 0 ) text = String.format(TITLE+" - track #%d", index);
			setText(text);
		}
	}
	/**
	 * Pair noteON/OFF トグルボタンモデル
	 */
	JToggleButton.ToggleButtonModel pairNoteOnOffModel;
	private class EventEditContext {
		/**
		 * 編集対象トラック
		 */
		private TrackEventListTableModel trackModel;
		/**
		 * tick位置入力モデル
		 */
		private TickPositionModel tickPositionModel = new TickPositionModel();
		/**
		 * 選択されたイベント
		 */
		private MidiEvent selectedMidiEvent = null;
		/**
		 * 選択されたイベントの場所
		 */
		private int selectedIndex = -1;
		/**
		 * 選択されたイベントのtick位置
		 */
		private long currentTick = 0;
		/**
		 * 上書きして削除対象にする変更前イベント（null可）
		 */
		private MidiEvent[] midiEventsToBeOverwritten;
		/**
		 * 選択したイベントを入力ダイアログなどに反映します。
		 * @param model 対象データモデル
		 */
		private void setSelectedEvent(TrackEventListTableModel trackModel) {
			this.trackModel = trackModel;
			SequenceTrackListTableModel sequenceTableModel = trackModel.getParent();
			int ppq = sequenceTableModel.getSequence().getResolution();
			eventDialog.midiMessageForm.durationForm.setPPQ(ppq);
			tickPositionModel.setSequenceIndex(sequenceTableModel.getSequenceTickIndex());

			selectedIndex = trackModel.getSelectionModel().getMinSelectionIndex();
			selectedMidiEvent = selectedIndex < 0 ? null : trackModel.getMidiEvent(selectedIndex);
			currentTick = selectedMidiEvent == null ? 0 : selectedMidiEvent.getTick();
			tickPositionModel.setTickPosition(currentTick);
		}
		public void setupForEdit(TrackEventListTableModel trackModel) {
			MidiEvent partnerEvent = null;
			eventDialog.midiMessageForm.setMessage(
				selectedMidiEvent.getMessage(),
				trackModel.getParent().getCharset()
			);
			if( eventDialog.midiMessageForm.isNote() ) {
				int partnerIndex = trackModel.getIndexOfPartnerFor(selectedIndex);
				if( partnerIndex < 0 ) {
					eventDialog.midiMessageForm.durationForm.setDuration(0);
				}
				else {
					partnerEvent = trackModel.getMidiEvent(partnerIndex);
					long partnerTick = partnerEvent.getTick();
					long duration = currentTick > partnerTick ?
						currentTick - partnerTick : partnerTick - currentTick ;
					eventDialog.midiMessageForm.durationForm.setDuration((int)duration);
				}
			}
			if(partnerEvent == null)
				midiEventsToBeOverwritten = new MidiEvent[] {selectedMidiEvent};
			else
				midiEventsToBeOverwritten = new MidiEvent[] {selectedMidiEvent, partnerEvent};
		}
		private Action jumpEventAction = new AbstractAction() {
			{ putValue(NAME,"Jump"); }
			public void actionPerformed(ActionEvent e) {
				long tick = tickPositionModel.getTickPosition();
				scrollToEventAt(tick);
				eventDialog.setVisible(false);
				trackModel = null;
			}
		};
		private Action pasteEventAction = new AbstractAction() {
			{ putValue(NAME,"Paste"); }
			public void actionPerformed(ActionEvent e) {
				long tick = tickPositionModel.getTickPosition();
				clipBoard.paste(trackModel, tick);
				scrollToEventAt(tick);
				// ペーストされたので変更フラグを立てる（曲の長さが変わるが、それも自動的にプレイリストに通知される）
				SequenceTrackListTableModel seqModel = trackModel.getParent();
				seqModel.setModified(true);
				eventDialog.setVisible(false);
				trackModel = null;
			}
		};
		private boolean applyEvent() {
			long tick = tickPositionModel.getTickPosition();
			MidiMessageForm form = eventDialog.midiMessageForm;
			SequenceTrackListTableModel seqModel = trackModel.getParent();
			MidiMessage msg = form.getMessage(seqModel.getCharset());
			if( msg == null ) {
				return false;
			}
			MidiEvent newMidiEvent = new MidiEvent(msg, tick);
			if( midiEventsToBeOverwritten != null ) {
				// 上書き消去するための選択済イベントがあった場合
				trackModel.removeMidiEvents(midiEventsToBeOverwritten);
			}
			if( ! trackModel.addMidiEvent(newMidiEvent) ) {
				System.out.println("addMidiEvent failure");
				return false;
			}
			if(pairNoteOnOffModel.isSelected() && form.isNote()) {
				ShortMessage sm = form.createPartnerMessage();
				if(sm == null)
					scrollToEventAt( tick );
				else {
					int duration = form.durationForm.getDuration();
					if( form.isNote(false) ) {
						duration = -duration;
					}
					long partnerTick = tick + (long)duration;
					if( partnerTick < 0L ) partnerTick = 0L;
					MidiEvent partner = new MidiEvent((MidiMessage)sm, partnerTick);
					if( ! trackModel.addMidiEvent(partner) ) {
						System.out.println("addMidiEvent failure (note on/off partner message)");
					}
					scrollToEventAt(partnerTick > tick ? partnerTick : tick);
				}
			}
			seqModel.setModified(true);
			eventDialog.setVisible(false);
			return true;
		}
	}
	private EventEditContext editContext = new EventEditContext();
	/**
	 * 指定のTick位置へジャンプするアクション
	 */
	Action queryJumpEventAction = new AbstractAction() {
		{
			putValue(NAME,"Jump to ...");
			setEnabled(false);
		}
		public void actionPerformed(ActionEvent e) {
			editContext.setSelectedEvent(getModel());
			eventDialog.openTickForm("Jump selection to", editContext.jumpEventAction);
		}
	};
	/**
	 * 新しいイベントの追加を行うアクション
	 */
	Action queryAddEventAction = new AbstractAction() {
		{
			putValue(NAME,"New");
			setEnabled(false);
		}
		public void actionPerformed(ActionEvent e) {
			TrackEventListTableModel model = getModel();
			editContext.setSelectedEvent(model);
			editContext.midiEventsToBeOverwritten = null;
			eventDialog.openEventForm("New MIDI event", eventCellEditor.applyEventAction, model.getChannel());
		}
	};
	/**
	 * MIDIイベントのコピー＆ペーストを行うためのクリップボード
	 */
	private class LocalClipBoard {
		private MidiEvent copiedEventsToPaste[];
		private int copiedEventsPPQ = 0;
		public void copy(TrackEventListTableModel model, boolean withRemove) {
			copiedEventsToPaste = model.getSelectedMidiEvents();
			copiedEventsPPQ = model.getParent().getSequence().getResolution();
			if( withRemove ) model.removeMidiEvents(copiedEventsToPaste);
			boolean en = (copiedEventsToPaste != null && copiedEventsToPaste.length > 0);
			queryPasteEventAction.setEnabled(en);
		}
		public void cut(TrackEventListTableModel model) {copy(model,true);}
		public void copy(TrackEventListTableModel model){copy(model,false);}
		public void paste(TrackEventListTableModel model, long tick) {
			model.addMidiEvents(copiedEventsToPaste, tick, copiedEventsPPQ);
		}
	}
	private LocalClipBoard clipBoard = new LocalClipBoard();
	/**
	 * 指定のTick位置へ貼り付けるアクション
	 */
	Action queryPasteEventAction = new AbstractAction() {
		{
			putValue(NAME,"Paste to ...");
			setEnabled(false);
		}
		public void actionPerformed(ActionEvent e) {
			editContext.setSelectedEvent(getModel());
			eventDialog.openTickForm("Paste to", editContext.pasteEventAction);
		}
	};
	/**
	 * イベントカットアクション
	 */
	public Action cutEventAction = new AbstractAction("Cut") {
		private static final String CONFIRM_MESSAGE =
				"Do you want to cut selected event ?\n選択したMIDIイベントを切り取りますか？";
		{ setEnabled(false); }
		@Override
		public void actionPerformed(ActionEvent event) {
			if( JOptionPane.showConfirmDialog(
					((JComponent)event.getSource()).getRootPane(),
					CONFIRM_MESSAGE,
					ChordHelperApplet.VersionInfo.NAME,
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION
			) clipBoard.cut(getModel());
		}
	};
	/**
	 * イベントコピーアクション
	 */
	public Action copyEventAction = new AbstractAction("Copy") {
		{ setEnabled(false); }
		@Override
		public void actionPerformed(ActionEvent e) { clipBoard.copy(getModel()); }
	};
	/**
	 * イベント削除アクション
	 */
	public Action deleteEventAction = new AbstractAction("Delete", MidiSequenceEditorDialog.deleteIcon) {
		private static final String CONFIRM_MESSAGE =
			"Do you want to delete selected event ?\n選択したMIDIイベントを削除しますか？";
		{ setEnabled(false); }
		@Override
		public void actionPerformed(ActionEvent event) {
			if( JOptionPane.showConfirmDialog(
					((JComponent)event.getSource()).getRootPane(),
					CONFIRM_MESSAGE,
					ChordHelperApplet.VersionInfo.NAME,
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION
			) {
				getModel().removeMidiEvents(getModel().getSelectedMidiEvents());
			}
		}
	};
	/**
	 * MIDIイベント表のセルエディタ
	 */
	private MidiEventCellEditor eventCellEditor;
	/**
	 * MIDIイベント表のセルエディタ
	 */
	class MidiEventCellEditor extends AbstractCellEditor implements TableCellEditor {
		/**
		 * MIDIイベントセルエディタを構築します。
		 */
		public MidiEventCellEditor() {
			eventDialog.midiMessageForm.setOutputMidiChannels(outputMidiDevice.getChannels());
			eventDialog.tickPositionInputForm.setModel(editContext.tickPositionModel);
			int index = TrackEventListTableModel.Column.MESSAGE.ordinal();
			getColumnModel().getColumn(index).setCellEditor(this);
		}
		/**
		 * セルをダブルクリックしないと編集できないようにします。
		 * @param e イベント（マウスイベント）
		 * @return 編集可能になったらtrue
		 */
		@Override
		public boolean isCellEditable(EventObject e) {
			return (e instanceof MouseEvent) ?
				((MouseEvent)e).getClickCount() == 2 : super.isCellEditable(e);
		}
		@Override
		public Object getCellEditorValue() { return null; }
		/**
		 * MIDIメッセージダイアログが閉じたときにセル編集を中止するリスナー
		 */
		private ComponentListener dialogComponentListener = new ComponentAdapter() {
			@Override
			public void componentHidden(ComponentEvent e) {
				fireEditingCanceled();
				// 用が済んだら当リスナーを除去
				eventDialog.removeComponentListener(this);
			}
		};
		/**
		 * 既存イベントを編集するアクション
		 */
		private Action editEventAction = new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				TrackEventListTableModel model = getModel();
				editContext.setSelectedEvent(model);
				if( editContext.selectedMidiEvent == null )
					return;
				editContext.setupForEdit(model);
				eventDialog.addComponentListener(dialogComponentListener);
				eventDialog.openEventForm("Change MIDI event", applyEventAction);
			}
		};
		/**
		 * イベント編集ボタン
		 */
		private JButton editEventButton = new JButton(editEventAction){{
			setHorizontalAlignment(JButton.LEFT);
		}};
		@Override
		public Component getTableCellEditorComponent(
			JTable table, Object value, boolean isSelected, int row, int column
		) {
			editEventButton.setText(value.toString());
			return editEventButton;
		}
		/**
		 * 入力したイベントを反映するアクション
		 */
		private Action applyEventAction = new AbstractAction() {
			{ putValue(NAME,"OK"); }
			public void actionPerformed(ActionEvent e) {
				if( editContext.applyEvent() ) fireEditingStopped();
			}
		};
	}
	/**
	 * スクロール可能なMIDIイベントテーブルビュー
	 */
	JScrollPane scrollPane = new JScrollPane(this);
	/**
	 * 指定の MIDI tick のイベントへスクロールします。
	 * @param tick MIDI tick
	 */
	public void scrollToEventAt(long tick) {
		int index = getModel().tickToIndex(tick);
		scrollPane.getVerticalScrollBar().setValue(index * getRowHeight());
		getSelectionModel().setSelectionInterval(index, index);
	}
}