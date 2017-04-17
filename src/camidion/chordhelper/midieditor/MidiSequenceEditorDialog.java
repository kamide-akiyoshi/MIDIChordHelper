package camidion.chordhelper.midieditor;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Arrays;
import java.util.EventObject;
import java.util.List;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import camidion.chordhelper.ButtonIcon;
import camidion.chordhelper.ChordHelperApplet;
import camidion.chordhelper.mididevice.MidiSequencerModel;
import camidion.chordhelper.mididevice.VirtualMidiDevice;
import camidion.chordhelper.music.MIDISpec;

/**
 * MIDIエディタ（MIDI Editor/Playlist for MIDI Chord Helper）
 *
 * @author
 *	Copyright (C) 2006-2016 Akiyoshi Kamide
 *	http://www.yk.rim.or.jp/~kamide/music/chordhelper/
 */
public class MidiSequenceEditorDialog extends JDialog {
	/**
	 * このダイアログを開きます。表示されていなければ表示し、すでに表示されていたら最前面に移動します。
	 */
	public void open() {
		if( isVisible() ) toFront(); else setVisible(true);
	}
	/**
	 * このダイアログを表示するボタン用アクション
	 */
	public final Action openAction = new AbstractAction("Edit/Playlist/Speed", new ButtonIcon(ButtonIcon.EDIT_ICON)) {
		{
			String tooltip = "MIDIシーケンスの編集／プレイリスト／再生速度調整";
			putValue(Action.SHORT_DESCRIPTION, tooltip);
		}
		@Override
		public void actionPerformed(ActionEvent e) { open(); }
	};
	/**
	 * ドロップされた複数のMIDIファイルを読み込むハンドラー
	 */
	public final TransferHandler transferHandler = new TransferHandler() {
		@Override
		public boolean canImport(TransferSupport support) {
			return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
		}
		@SuppressWarnings("unchecked")
		@Override
		public boolean importData(TransferSupport support) {
			try {
				Object data = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
				play((List<File>)data);
				return true;
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(
						MidiSequenceEditorDialog.this, ex,
						ChordHelperApplet.VersionInfo.NAME,
						JOptionPane.ERROR_MESSAGE);
				return false;
			}
		}
	};
	/**
	 * このエディタダイアログが表示しているプレイリストモデルを返します。
	 * @return プレイリストモデル
	 */
	public PlaylistTableModel getPlaylistModel() {
		return sequenceListTable.getModel();
	}
	/**
	 * 指定されたリストに格納されたMIDIファイルを読み込んで再生します。
	 * すでに再生されていた場合、このエディタダイアログを表示します。
	 * @param fileList 読み込むMIDIファイルのリスト
	 */
	public void play(List<File> fileList) {
		play(sequenceListTable.add(fileList));
	}
	/**
	 * 指定されたインデックス値（先頭が0）のMIDIシーケンスから再生します。
	 * すでに再生されていた場合、このエディタダイアログを表示します。
	 * @param index プレイリスト内にあるMIDIシーケンスのインデックス値
	 */
	public void play(int index) {
		try {
			PlaylistTableModel playlist = sequenceListTable.getModel();
			MidiSequencerModel sequencerModel = playlist.getSequencerModel();
			if( sequencerModel.getSequencer().isRunning() ) { open(); return; }
			if( index >= 0 ) playlist.play(index);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, e, ChordHelperApplet.VersionInfo.NAME, JOptionPane.ERROR_MESSAGE);
		}
	}

	static final Icon deleteIcon = new ButtonIcon(ButtonIcon.X_ICON);
	/**
	 * 新しいMIDIシーケンスを生成するダイアログ
	 */
	public NewSequenceDialog newSequenceDialog;
	/**
	 * プレイリストビュー（シーケンスリスト）
	 */
	public SequenceListTable sequenceListTable;
	/**
	 * MIDIトラックリストテーブルビュー（選択中のシーケンスの中身）
	 */
	private TrackListTable trackListTable;
	/**
	 * MIDIイベントリストテーブルビュー（選択中のトラックの中身）
	 */
	private EventListTable eventListTable;
	/**
	 * MIDIイベント入力ダイアログ（イベント入力とイベント送出で共用）
	 */
	public MidiEventDialog eventDialog = new MidiEventDialog();
	/**
	 * 操作音を鳴らすMIDI出力デバイス
	 */
	private VirtualMidiDevice outputMidiDevice;
	/**
	 * シーケンス（トラックリスト）テーブルビュー
	 */
	public class TrackListTable extends JTable {
		/**
		 * トラックリストテーブルビューを構築します。
		 * @param model シーケンス（トラックリスト）データモデル
		 */
		public TrackListTable(SequenceTrackListTableModel model) {
			super(model, null, model.getSelectionModel());
			//
			// 録音対象のMIDIチャンネルをコンボボックスで選択できるようにする
			getColumnModel()
				.getColumn(SequenceTrackListTableModel.Column.RECORD_CHANNEL.ordinal())
				.setCellEditor(new DefaultCellEditor(new JComboBox<String>(){{
					addItem("OFF");
					for(int i=1; i <= MIDISpec.MAX_CHANNELS; i++) addItem(String.format("%d", i));
					addItem("ALL");
				}}));
			setAutoCreateColumnsFromModel(false);
			model.getParent().sequenceListSelectionModel.addListSelectionListener(titleLabel);
			TableColumnModel colModel = getColumnModel();
			Arrays.stream(SequenceTrackListTableModel.Column.values()).forEach(c->
				colModel.getColumn(c.ordinal()).setPreferredWidth(c.preferredWidth)
			);
		}
		/**
		 * このテーブルビューが表示するデータを提供する
		 * シーケンス（トラックリスト）データモデルを返します。
		 * @return シーケンス（トラックリスト）データモデル
		 */
		@Override
		public SequenceTrackListTableModel getModel() {
			return (SequenceTrackListTableModel) super.getModel();
		}
		/**
		 * タイトルラベル
		 */
		TitleLabel titleLabel = new TitleLabel();
		/**
		 * 親テーブルの選択シーケンスの変更に反応する
		 * 曲番号表示付きタイトルラベル
		 */
		private class TitleLabel extends JLabel implements ListSelectionListener {
			private static final String TITLE = "Tracks";
			public TitleLabel() { setText(TITLE); }
			@Override
			public void valueChanged(ListSelectionEvent event) {
				if( event.getValueIsAdjusting() ) return;
				SequenceTrackListTableModel oldModel = getModel();
				SequenceTrackListTableModel newModel = oldModel.getParent().getSelectedSequenceModel();
				if( oldModel == newModel ) return;
				//
				// MIDIチャンネル選択中のときはキャンセルする
				cancelCellEditing();
				//
				String text = TITLE;
				ListSelectionModel sm = oldModel.getParent().sequenceListSelectionModel;
				if( ! sm.isSelectionEmpty() ) {
					int index = sm.getMinSelectionIndex();
					if( index >= 0 ) text = String.format(text+" - MIDI file #%d", index);
				}
				setText(text);
				if( newModel == null ) {
					newModel = oldModel.getParent().emptyTrackListTableModel;
					addTrackAction.setEnabled(false);
				}
				else {
					addTrackAction.setEnabled(true);
				}
				oldModel.getSelectionModel().removeListSelectionListener(trackSelectionListener);
				setModel(newModel);
				setSelectionModel(newModel.getSelectionModel());
				newModel.getSelectionModel().addListSelectionListener(trackSelectionListener);
				trackSelectionListener.valueChanged(null);
			}
		}
		/**
		 * トラック選択リスナー
		 */
		ListSelectionListener trackSelectionListener = new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if( e != null && e.getValueIsAdjusting() ) return;
				ListSelectionModel tlsm = getModel().getSelectionModel();
				deleteTrackAction.setEnabled(! tlsm.isSelectionEmpty());
				eventListTable.titleLabel.update(tlsm, getModel());
			}
		};
		/**
		 * {@inheritDoc}
		 *
		 * <p>このトラックリストテーブルのデータが変わったときに編集を解除します。
		 * 例えば、イベントが編集された場合や、
		 * シーケンサーからこのモデルが外された場合がこれに該当します。
		 * </p>
		 */
		@Override
		public void tableChanged(TableModelEvent e) {
			super.tableChanged(e);
			cancelCellEditing();
		}
		/**
		 * このトラックリストテーブルが編集モードになっていたら解除します。
		 */
		private void cancelCellEditing() {
			TableCellEditor currentCellEditor = getCellEditor();
			if( currentCellEditor != null ) currentCellEditor.cancelCellEditing();
		}
		/**
		 * トラック追加アクション
		 */
		Action addTrackAction = new AbstractAction("New") {
			{
				String tooltip = "Append new track - 新しいトラックの追加";
				putValue(Action.SHORT_DESCRIPTION, tooltip);
				setEnabled(false);
			}
			@Override
			public void actionPerformed(ActionEvent e) { getModel().createTrack(); }
		};
		/**
		 * トラック削除アクション
		 */
		Action deleteTrackAction = new AbstractAction("Delete", deleteIcon) {
			public static final String CONFIRM_MESSAGE =
					"Do you want to delete selected track ?\n選択したトラックを削除しますか？";
			{
				putValue(Action.SHORT_DESCRIPTION, "Delete selected track - 選択したトラックを削除");
				setEnabled(false);
			}
			@Override
			public void actionPerformed(ActionEvent e) {
				if( JOptionPane.showConfirmDialog(
						TrackListTable.this.getRootPane(),
						CONFIRM_MESSAGE,
						ChordHelperApplet.VersionInfo.NAME,
						JOptionPane.YES_NO_OPTION,
						JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION
				) getModel().deleteSelectedTracks();
			}
		};
	}

	/**
	 * MIDIイベントリストテーブルビュー（選択中のトラックの中身）
	 */
	public class EventListTable extends JTable {
		/**
		 * 新しいイベントリストテーブルを構築します。
		 * <p>データモデルとして一つのトラックのイベントリストを指定できます。
		 * トラックを切り替えたいときは {@link #setModel(TableModel)}
		 * でデータモデルを異なるトラックのものに切り替えます。
		 * </p>
		 *
		 * @param model トラック（イベントリスト）データモデル
		 */
		public EventListTable(TrackEventListTableModel model) {
			super(model, null, model.getSelectionModel());
			//
			// 列モデルにセルエディタを設定
			eventCellEditor = new MidiEventCellEditor();
			setAutoCreateColumnsFromModel(false);
			//
			eventSelectionListener = new EventSelectionListener();
			titleLabel = new TitleLabel();
			//
			TableColumnModel cm = getColumnModel();
			Arrays.stream(TrackEventListTableModel.Column.values()).forEach(c->
				cm.getColumn(c.ordinal()).setPreferredWidth(c.preferredWidth)
			);
		}
		/**
		 * このテーブルビューが表示するデータを提供する
		 * トラック（イベントリスト）データモデルを返します。
		 * @return トラック（イベントリスト）データモデル
		 */
		@Override
		public TrackEventListTableModel getModel() {
			return (TrackEventListTableModel) super.getModel();
		}
		/**
		 * タイトルラベル
		 */
		TitleLabel titleLabel;
		/**
		 * 親テーブルの選択トラックの変更に反応する
		 * トラック番号つきタイトルラベル
		 */
		private class TitleLabel extends JLabel {
			private static final String TITLE = "MIDI Events";
			public TitleLabel() { super(TITLE); }
			public void update(ListSelectionModel tlsm, SequenceTrackListTableModel sequenceModel) {
				String text = TITLE;
				TrackEventListTableModel oldTrackModel = getModel();
				int index = tlsm.getMinSelectionIndex();
				if( index >= 0 ) {
					text = String.format(TITLE+" - track #%d", index);
				}
				setText(text);
				TrackEventListTableModel newTrackModel = sequenceModel.getSelectedTrackModel();
				if( oldTrackModel == newTrackModel )
					return;
				if( newTrackModel == null ) {
					newTrackModel = getModel().getParent().getParent().emptyEventListTableModel;
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
				oldTrackModel.getSelectionModel().removeListSelectionListener(eventSelectionListener);
				setModel(newTrackModel);
				setSelectionModel(newTrackModel.getSelectionModel());
				newTrackModel.getSelectionModel().addListSelectionListener(eventSelectionListener);
			}
		}

		/**
		 * イベント選択リスナー
		 */
		private EventSelectionListener eventSelectionListener;
		/**
		 * 選択イベントの変更に反応するリスナー
		 */
		private class EventSelectionListener implements ListSelectionListener {
			public EventSelectionListener() {
				getModel().getSelectionModel().addListSelectionListener(this);
			}
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if( e.getValueIsAdjusting() )
					return;
				if( getSelectionModel().isSelectionEmpty() ) {
					queryPasteEventAction.setEnabled(false);
					copyEventAction.setEnabled(false);
					deleteEventAction.setEnabled(false);
					cutEventAction.setEnabled(false);
				}
				else {
					copyEventAction.setEnabled(true);
					deleteEventAction.setEnabled(true);
					cutEventAction.setEnabled(true);
					TrackEventListTableModel trackModel = getModel();
					int minIndex = getSelectionModel().getMinSelectionIndex();
					MidiEvent midiEvent = trackModel.getMidiEvent(minIndex);
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
						int maxIndex = getSelectionModel().getMaxSelectionIndex();
						int partnerIndex;
						for( int i=minIndex; i<=maxIndex; i++ ) {
							if( ! getSelectionModel().isSelectedIndex(i) ) continue;
							partnerIndex = trackModel.getIndexOfPartnerFor(i);
							if( partnerIndex >= 0 && ! getSelectionModel().isSelectedIndex(partnerIndex) )
								getSelectionModel().addSelectionInterval(partnerIndex, partnerIndex);
						}
					}
				}
			}
		}
		/**
		 * Pair noteON/OFF トグルボタンモデル
		 */
		private JToggleButton.ToggleButtonModel
			pairNoteOnOffModel = new JToggleButton.ToggleButtonModel() {
				{
					addItemListener(e->eventDialog.midiMessageForm.durationForm.setEnabled(isSelected()));
					setSelected(true);
				}
			};
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
					trackModel.getParent().charset
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
				MidiMessage msg = form.getMessage(seqModel.charset);
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
			public void actionPerformed(ActionEvent e) {
				if( JOptionPane.showConfirmDialog(
						EventListTable.this.getRootPane(),
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
		public Action deleteEventAction = new AbstractAction("Delete", deleteIcon) {
			private static final String CONFIRM_MESSAGE =
				"Do you want to delete selected event ?\n選択したMIDIイベントを削除しますか？";
			{ setEnabled(false); }
			@Override
			public void actionPerformed(ActionEvent e) {
				if( JOptionPane.showConfirmDialog(
						EventListTable.this.getRootPane(),
						CONFIRM_MESSAGE,
						ChordHelperApplet.VersionInfo.NAME,
						JOptionPane.YES_NO_OPTION,
						JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION
				) getModel().removeSelectedMidiEvents();
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
		private JScrollPane scrollPane = new JScrollPane(this);
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

	/**
	 * 新しい {@link MidiSequenceEditorDialog} を構築します。
	 * @param playlistTableModel このエディタが参照するプレイリストモデル
	 * @param outputMidiDevice イベントテーブルの操作音出力先MIDIデバイス
	 * @param midiDeviceDialogOpenAction MIDIデバイスダイアログを開くアクション
	 */
	public MidiSequenceEditorDialog(PlaylistTableModel playlistTableModel, VirtualMidiDevice outputMidiDevice, Action midiDeviceDialogOpenAction) {
		this.outputMidiDevice = outputMidiDevice;
		sequenceListTable = new SequenceListTable(playlistTableModel, midiDeviceDialogOpenAction);
		trackListTable = new TrackListTable(playlistTableModel.emptyTrackListTableModel);
		eventListTable = new EventListTable(playlistTableModel.emptyEventListTableModel);
		newSequenceDialog = new NewSequenceDialog(playlistTableModel, outputMidiDevice);
		setTitle("MIDI Editor/Playlist - "+ChordHelperApplet.VersionInfo.NAME);
		setBounds( 150, 200, 900, 500 );
		setLayout(new FlowLayout());
		setTransferHandler(transferHandler);
		//
		// パネルレイアウト
		JPanel playlistPanel = new JPanel() {{
			JPanel playlistOperationPanel = new JPanel() {{
				setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
				add(Box.createRigidArea(new Dimension(10, 0)));
				add(new JButton(newSequenceDialog.openAction) {
					{ setMargin(ChordHelperApplet.ZERO_INSETS); }
				});
				if( sequenceListTable.midiFileChooser != null ) {
					add( Box.createRigidArea(new Dimension(5, 0)) );
					add(new JButton(sequenceListTable.midiFileChooser.openMidiFileAction) {
						{ setMargin(ChordHelperApplet.ZERO_INSETS); }
					});
				}
				if(sequenceListTable.base64EncodeAction != null) {
					add(Box.createRigidArea(new Dimension(5, 0)));
					add(new JButton(sequenceListTable.base64EncodeAction) {{ setMargin(ChordHelperApplet.ZERO_INSETS); }});
				}
				add(Box.createRigidArea(new Dimension(5, 0)));
				add(new JButton(playlistTableModel.getMoveToTopAction()) {
					{ setMargin(ChordHelperApplet.ZERO_INSETS); }
				});
				add(Box.createRigidArea(new Dimension(5, 0)));
				add(new JButton(playlistTableModel.getMoveToBottomAction()) {
					{ setMargin(ChordHelperApplet.ZERO_INSETS); }
				});
				if( sequenceListTable.midiFileChooser != null ) {
					add(Box.createRigidArea(new Dimension(5, 0)));
					add(new JButton(sequenceListTable.midiFileChooser.saveMidiFileAction) {
						{ setMargin(ChordHelperApplet.ZERO_INSETS); }
					});
				}
				add( Box.createRigidArea(new Dimension(5, 0)) );
				add(new JButton(sequenceListTable.deleteSequenceAction) {
					{ setMargin(ChordHelperApplet.ZERO_INSETS); }
				});
				add( Box.createRigidArea(new Dimension(5, 0)) );
				add(new SequencerSpeedSlider(playlistTableModel.getSequencerModel().speedSliderModel));
				add( Box.createRigidArea(new Dimension(5, 0)) );
				add(new JPanel() {{
					setBorder(new EtchedBorder());
					MidiSequencerModel sequencerModel = playlistTableModel.getSequencerModel();
					add(new JLabel("Sync Master"));
					add(new JComboBox<Sequencer.SyncMode>(sequencerModel.masterSyncModeModel));
					add(new JLabel("Slave"));
					add(new JComboBox<Sequencer.SyncMode>(sequencerModel.slaveSyncModeModel));
				}});
			}};
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			add(new JScrollPane(sequenceListTable));
			add(Box.createRigidArea(new Dimension(0, 10)));
			add(playlistOperationPanel);
			add(Box.createRigidArea(new Dimension(0, 10)));
		}};
		JPanel trackListPanel = new JPanel() {{
			JPanel trackListOperationPanel = new JPanel() {{
				add(new JButton(trackListTable.addTrackAction) {{ setMargin(ChordHelperApplet.ZERO_INSETS); }});
				add(new JButton(trackListTable.deleteTrackAction) {{ setMargin(ChordHelperApplet.ZERO_INSETS); }});
			}};
			setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
			add(trackListTable.titleLabel);
			add(Box.createRigidArea(new Dimension(0, 5)));
			add(new JScrollPane(trackListTable));
			add(Box.createRigidArea(new Dimension(0, 5)));
			add(trackListOperationPanel);
		}};
		JPanel eventListPanel = new JPanel() {{
			JPanel eventListOperationPanel = new JPanel() {{
				add(new JCheckBox("Pair NoteON/OFF") {{
					setModel(eventListTable.pairNoteOnOffModel);
					setToolTipText("NoteON/OFFをペアで同時選択する");
				}});
				add(new JButton(eventListTable.queryJumpEventAction) {{ setMargin(ChordHelperApplet.ZERO_INSETS); }});
				add(new JButton(eventListTable.queryAddEventAction) {{ setMargin(ChordHelperApplet.ZERO_INSETS); }});
				add(new JButton(eventListTable.copyEventAction) {{ setMargin(ChordHelperApplet.ZERO_INSETS); }});
				add(new JButton(eventListTable.cutEventAction) {{ setMargin(ChordHelperApplet.ZERO_INSETS); }});
				add(new JButton(eventListTable.queryPasteEventAction) {{ setMargin(ChordHelperApplet.ZERO_INSETS); }});
				add(new JButton(eventListTable.deleteEventAction) {{ setMargin(ChordHelperApplet.ZERO_INSETS); }});
			}};
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			add(eventListTable.titleLabel);
			add(eventListTable.scrollPane);
			add(eventListOperationPanel);
		}};
		Container cp = getContentPane();
		cp.setLayout(new BoxLayout(cp, BoxLayout.Y_AXIS));
		cp.add(Box.createVerticalStrut(2));
		cp.add(
			new JSplitPane(JSplitPane.VERTICAL_SPLIT, playlistPanel,
				new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, trackListPanel, eventListPanel) {{
					setDividerLocation(300);
				}}
			) {{
				setDividerLocation(160);
			}}
		);
	}

}
