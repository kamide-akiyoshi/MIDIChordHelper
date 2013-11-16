
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Track;
import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.Action;
import javax.swing.BoundedRangeModel;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

/**
 * MIDIエディタ（MIDI Editor/Playlist for MIDI Chord Helper）
 *
 * @author
 *	Copyright (C) 2006-2013 Akiyoshi Kamide
 *	http://www.yk.rim.or.jp/~kamide/music/chordhelper/
 */
class MidiEditor extends JDialog
	implements DropTargetListener, ListSelectionListener, ActionListener
{
	public static final Insets ZERO_INSETS = new Insets(0,0,0,0);
	/**
	 * プレイリストのモデル
	 */
	SequenceListTableModel sequenceListTableModel;
	/**
	 * このMIDIエディタの仮想MIDIデバイス
	 */
	VirtualMidiDevice virtualMidiDevice = new AbstractVirtualMidiDevice() {
		class MyInfo extends Info {
			protected MyInfo() {
				super("MIDI Editor","Unknown vendor","MIDI sequence editor","");
			}
		}
		{
			info = new MyInfo();
			// 送信のみなので MIDI IN はサポートしない
			setMaxReceivers(0);
		}
	};
	/**
	 * 新しいMIDIシーケンスを生成するダイアログ
	 */
	NewSequenceDialog newSequenceDialog = new NewSequenceDialog(this) {{
		setChannels(virtualMidiDevice.getChannels());
	}};
	/**
	 * MIDIイベント入力ダイアログ
	 */
	MidiEventDialog	eventDialog = new MidiEventDialog();
	/**
	 * MIDIシーケンス選択状態
	 */
	ListSelectionModel seqSelectionModel;
	/**
	 * MIDIトラック選択状態
	 */
	private ListSelectionModel trackSelectionModel;
	/**
	 * MIDIイベント選択状態
	 */
	private ListSelectionModel eventSelectionModel;

	/**
	 * MIDIシーケンスリストテーブルビュー
	 */
	private JTable sequenceListTableView;
	/**
	 * MIDIトラックリストテーブルビュー
	 */
	private JTable trackListTableView;
	/**
	 * MIDIイベントリストテーブルビュー
	 */
	private JTable eventListTableView;
	/**
	 * スクロール可能なMIDIイベントテーブルビュー
	 */
	private JScrollPane scrollableEventTableView;
	/**
	 * 全MIDIシーケンス合計時間表示ラベル
	 */
	private class TotalTimeLabel extends JLabel implements TableModelListener {
		SequenceListTableModel model;
		public TotalTimeLabel(SequenceListTableModel model) {
			(this.model = model).addTableModelListener(this);
			update();
		}
		private void update() {
			int sec = model.getTotalSeconds();
			String str = String.format("MIDI file playlist - Total length = %02d:%02d", sec/60, sec%60);
			setText(str);
		}
		/**
		 * プレイリスト上でシーケンスが増減した場合、
		 * 合計時間が変わるので表示を更新します。
		 */
		@Override
		public void tableChanged(TableModelEvent e) {
			switch( e.getType() ) {
			case TableModelEvent.INSERT:
			case TableModelEvent.DELETE: update(); break;
			default: break;
			}
		}
	}
	/**
	 * MIDIトラック数表示ラベル
	 */
	private JLabel tracksLabel = new JLabel("Tracks");
	/**
	 * MIDIイベント数表示ラベル
	 */
	private JLabel midiEventsLabel = new JLabel("No track selected");
	/**
	 * BASE64テキスト入力ダイアログ
	 */
	private Base64Dialog base64Dialog = new Base64Dialog(this);
	/**
	 * BASE64エンコードボタン（ライブラリが見えている場合のみ有効）
	 */
	private JButton base64EncodeButton;

	/**
	 * ファイル選択ダイアログ（アプレットでは使用不可）
	 */
	private JFileChooser fileChooser;
	/**
	 * MIDIシーケンス追加ボタン（ファイル選択ダイアログが利用できる場合のみ有効）
	 */
	private JButton addMidiFileButton;
	/**
	 * MIDIファイル保存ボタン（ファイル選択ダイアログが利用できる場合のみ有効）
	 */
	private JButton saveMidiFileButton;

	/**
	 * MIDIシーケンス削除ボタン
	 */
	private JButton deleteSequenceButton;
	/**
	 * MIDIシーケンスジャンプボタン
	 */
	private JButton jumpSequenceButton = new JButton("Jump") {{
		setToolTipText("Move to selected song - 選択した曲へ進む");
		setMargin(ZERO_INSETS);
		addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					load(seqSelectionModel.getMinSelectionIndex());
				}
			}
		);
	}};
	/**
	 * MIDIトラック追加ボタン
	 */
	private JButton addTrackButton = new JButton("New") {{
		setMargin(ZERO_INSETS);
		addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					int index = sequenceListTableModel.getSequenceModel(seqSelectionModel).createTrack();
					trackSelectionModel.setSelectionInterval(index, index);
					sequenceListTableModel.fireSequenceChanged(seqSelectionModel);
				}
			}
		);
	}};;
	/**
	 * MIDIトラック除去ボタン
	 */
	private JButton removeTrackButton;
	/**
	 * MIDIイベント除去ボタン
	 */
	private JButton removeEventButton;
	/**
	 * Pair note on/off チェックボックス
	 */
	private JCheckBox pairNoteCheckbox;

	/**
	 * MIDIイベント表のセルエディタ
	 */
	class MidiEventCellEditor extends AbstractCellEditor implements TableCellEditor {
		/**
		 * 削除対象にする変更前イベント（null可）
		 */
		private MidiEvent[] midiEventsToBeRemoved;
		/**
		 * 対象トラック
		 */
		private MidiTrackTableModel midiTrackTableModel;
		/**
		 * 対象シーケンス
		 */
		private MidiSequenceTableModel sequenceTableModel;
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
		 * tick位置入力モデル
		 */
		private TickPositionModel tickPositionModel = new TickPositionModel();
		/**
		 * Pair noteON/OFF トグルボタンモデル
		 */
		private JToggleButton.ToggleButtonModel pairNoteOnOffModel =
			new JToggleButton.ToggleButtonModel();

		private void setSelectedEvent() {
			sequenceTableModel = sequenceListTableModel.getSequenceModel(seqSelectionModel);
			eventDialog.midiMessageForm.durationForm.setPPQ(sequenceTableModel.getSequence().getResolution());
			tickPositionModel.setSequenceIndex(sequenceTableModel.getSequenceTickIndex());
			selectedIndex = -1;
			currentTick = 0;
			selectedMidiEvent = null;
			midiTrackTableModel = (MidiTrackTableModel)eventListTableView.getModel();
			if( ! eventSelectionModel.isSelectionEmpty() ) {
				selectedIndex = eventSelectionModel.getMinSelectionIndex();
				selectedMidiEvent = midiTrackTableModel.getMidiEvent(selectedIndex);
				currentTick = selectedMidiEvent.getTick();
				tickPositionModel.setTickPosition(currentTick);
			}
		}
		/**
		 * キャンセルするアクション
		 */
		Action cancelAction = new AbstractAction() {
			{ putValue(NAME,"Cancel"); }
			public void actionPerformed(ActionEvent e) {
				fireEditingCanceled();
				eventDialog.setVisible(false);
			}
		};
		/**
		 * 指定のTick位置へジャンプするアクション
		 */
		Action queryJumpEventAction = new AbstractAction() {
			private Action jumpEventAction = new AbstractAction() {
				{ putValue(NAME,"Jump"); }
				public void actionPerformed(ActionEvent e) {
					scrollToEventAt(tickPositionModel.getTickPosition());
					eventDialog.setVisible(false);
				}
			};
			{ putValue(NAME,"Jump to ..."); }
			public void actionPerformed(ActionEvent e) {
				setSelectedEvent();
				eventDialog.setTitle("Jump selection to");
				eventDialog.okButton.setAction(jumpEventAction);
				eventDialog.openTickForm();
			}
		};
		/**
		 * 指定のTick位置へ貼り付けるアクション
		 */
		Action queryPasteEventAction = new AbstractAction() {
			{ putValue(NAME,"Paste to ..."); }
			private Action pasteEventAction = new AbstractAction() {
				{ putValue(NAME,"Paste"); }
				public void actionPerformed(ActionEvent e) {
					long tick = tickPositionModel.getTickPosition();
					((MidiTrackTableModel)eventListTableView.getModel()).addMidiEvents(
						copiedEventsToPaste, tick, copiedEventsPPQ
					);
					scrollToEventAt(tick);
					sequenceListTableModel.fireSequenceChanged(seqSelectionModel);
					eventDialog.setVisible(false);
				}
			};
			public void actionPerformed(ActionEvent e) {
				setSelectedEvent();
				eventDialog.setTitle("Paste to");
				eventDialog.okButton.setAction(pasteEventAction);
				eventDialog.openTickForm();
			}
		};
		/**
		 * 新しいイベントの追加を行うアクション
		 */
		Action queryAddEventAction = new AbstractAction() {
			{ putValue(NAME,"New"); }
			public void actionPerformed(ActionEvent e) {
				setSelectedEvent();
				midiEventsToBeRemoved = null;
				eventDialog.setTitle("Add a new MIDI event");
				eventDialog.okButton.setAction(addEventAction);
				int ch = midiTrackTableModel.getChannel();
				if( ch >= 0 ) {
					eventDialog.midiMessageForm.channelText.setSelectedChannel(ch);
				}
				eventDialog.openEventForm();
			}
		};
		/**
		 * イベントの追加（または変更）を行うアクション
		 */
		private Action addEventAction = new AbstractAction() {
			{ putValue(NAME,"OK"); }
			public void actionPerformed(ActionEvent e) {
				long tick = tickPositionModel.getTickPosition();
				MidiMessage msg = eventDialog.midiMessageForm.getMessage();
				MidiEvent newMidiEvent = new MidiEvent(msg,tick);
				if( midiEventsToBeRemoved != null ) {
					midiTrackTableModel.removeMidiEvents(midiEventsToBeRemoved);
				}
				if( ! midiTrackTableModel.addMidiEvent(newMidiEvent) ) {
					System.out.println("addMidiEvent failure");
					return;
				}
				if(pairNoteOnOffModel.isSelected() && eventDialog.midiMessageForm.isNote()) {
					ShortMessage sm = eventDialog.midiMessageForm.getPartnerMessage();
					if( sm == null ) scrollToEventAt( tick );
					else {
						int duration = eventDialog.midiMessageForm.durationForm.getDuration();
						if( eventDialog.midiMessageForm.isNote(false) ) { // Note Off
							duration = -duration;
						}
						long partnerTick = tick + (long)duration;
						if( partnerTick < 0L ) partnerTick = 0L;
						MidiEvent partner_midi_event =
								new MidiEvent( (MidiMessage)sm, partnerTick );
						if( ! midiTrackTableModel.addMidiEvent(partner_midi_event) ) {
							System.out.println("addMidiEvent failure (note on/off partner message)");
						}
						scrollToEventAt( partnerTick > tick ? partnerTick : tick );
					}
				}
				sequenceListTableModel.fireSequenceChanged(sequenceTableModel);
				eventDialog.setVisible(false);
				fireEditingStopped();
			}
		};
		/**
		 * イベント編集ボタン
		 */
		private JButton editEventButton = new JButton() {{
			setHorizontalAlignment(JButton.LEFT);
		}};
		/**
		 * MIDIイベント表のセルエディタを構築します。
		 */
		public MidiEventCellEditor() {
			eventDialog.cancelButton.setAction(cancelAction);
			eventDialog.midiMessageForm.setOutputMidiChannels(virtualMidiDevice.getChannels());
			eventDialog.tickPositionInputForm.setModel(tickPositionModel);
			editEventButton.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						setSelectedEvent();
						if( selectedMidiEvent == null ) return;
						MidiEvent partnerEvent = null;
						eventDialog.midiMessageForm.setMessage(selectedMidiEvent.getMessage());
						if( eventDialog.midiMessageForm.isNote() ) {
							int partnerIndex = midiTrackTableModel.getIndexOfPartnerFor(selectedIndex);
							if( partnerIndex < 0 ) {
								eventDialog.midiMessageForm.durationForm.setDuration(0);
							}
							else {
								partnerEvent = midiTrackTableModel.getMidiEvent(partnerIndex);
								long partnerTick = partnerEvent.getTick();
								long duration = currentTick > partnerTick ?
									currentTick - partnerTick : partnerTick - currentTick ;
								eventDialog.midiMessageForm.durationForm.setDuration((int)duration);
							}
						}
						MidiEvent events[];
						if( partnerEvent == null ) {
							events = new MidiEvent[1];
							events[0] = selectedMidiEvent;
						}
						else {
							events = new MidiEvent[2];
							events[0] = selectedMidiEvent;
							events[1] = partnerEvent;
						}
						midiEventsToBeRemoved = events;
						eventDialog.setTitle("Change MIDI event");
						eventDialog.okButton.setAction(addEventAction);
						eventDialog.openEventForm();
					}
				}
			);
			pairNoteOnOffModel.addItemListener(
				new ItemListener() {
					public void itemStateChanged(ItemEvent e) {
						eventDialog.midiMessageForm.durationForm.setEnabled(
							pairNoteOnOffModel.isSelected()
						);
					}
				}
			);
			pairNoteOnOffModel.setSelected(true);
		}
		public boolean isCellEditable(EventObject e) {
			// ダブルクリックで編集
			return e instanceof MouseEvent && ((MouseEvent)e).getClickCount() == 2;
		}
		public Object getCellEditorValue() {
			return "";
		}
		public Component getTableCellEditorComponent(
			JTable table, Object value, boolean isSelected,
			int row, int column
		) {
			editEventButton.setText((String)value);
			return editEventButton;
		}
	}

	/**
	 * MIDIシーケンサモデル
	 */
	private MidiSequencerModel sequencerModel;
	/**
	 * 曲の先頭または前の曲へ戻るアクション
	 */
	public Action moveToTopAction = new AbstractAction() {
		{
			putValue( SHORT_DESCRIPTION,
				"Move to top or previous song - 曲の先頭または前の曲へ戻る"
			);
			putValue( LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.TOP_ICON) );
		}
		public void actionPerformed(ActionEvent event) {
			if( sequencerModel.getSequencer().getTickPosition() <= 40 )
				loadNext(-1);
			sequencerModel.setValue(0);
		}
	};
	/**
	 * 次の曲へ進むアクション
	 */
	public Action moveToBottomAction = new AbstractAction() {
		{
			putValue( SHORT_DESCRIPTION, "Move to next song - 次の曲へ進む" );
			putValue( LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.BOTTOM_ICON) );
		}
		public void actionPerformed(ActionEvent event) {
			if(loadNext(1)) sequencerModel.setValue(0);
		}
	};

	/**
	 * MIDIイベントセルエディタ
	 */
	private MidiEventCellEditor eventCellEditor = new MidiEventCellEditor();
	/**
	 * イベント追加ボタン
	 */
	private JButton addEventButton = new JButton(eventCellEditor.queryAddEventAction) {{
		setMargin(ZERO_INSETS);
	}};
	/**
	 * イベントジャンプボタン
	 */
	private JButton jumpEventButton = new JButton(eventCellEditor.queryJumpEventAction) {{
		setMargin(ZERO_INSETS);
	}};
	/**
	 * イベント貼り付けボタン
	 */
	private JButton pasteEventButton = new JButton(eventCellEditor.queryPasteEventAction) {{
		setMargin(ZERO_INSETS);
	}};
	/**
	 * ペースト用にコピーされたMIDIイベントの配列
	 */
	private MidiEvent copiedEventsToPaste[];
	/**
	 * ペースト用にコピーされたMIDIイベントのタイミング解像度
	 */
	private int copiedEventsPPQ = 0;
	/**
	 * イベントカットボタン
	 */
	private JButton cutEventButton = new JButton("Cut") {{
		setMargin(ZERO_INSETS);
		addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if( ! confirm("Do you want to cut selected event ?\n選択したMIDIイベントを切り取りますか？"))
						return;
					MidiTrackTableModel trackTableModel = (MidiTrackTableModel)eventListTableView.getModel();
					copiedEventsToPaste = trackTableModel.getMidiEvents(eventSelectionModel);
					copiedEventsPPQ = sequenceListTableModel.getSequenceModel(seqSelectionModel).getSequence().getResolution();
					trackTableModel.removeMidiEvents(copiedEventsToPaste);
					sequenceListTableModel.fireSequenceChanged(seqSelectionModel);
				}
			}
		);
	}};
	/**
	 * イベントコピーボタン
	 */
	private JButton copyEventButton = new JButton("Copy") {{
		setMargin(ZERO_INSETS);
		addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					MidiTrackTableModel trackTableModel = (MidiTrackTableModel)eventListTableView.getModel();
					copiedEventsToPaste = trackTableModel.getMidiEvents(eventSelectionModel);
					copiedEventsPPQ = sequenceListTableModel.getSequenceModel(seqSelectionModel).getSequence().getResolution();
					updateButtonStatus();
				}
			}
		);
	}};
	/**
	 * 再生／一時停止ボタン
	 */
	private JToggleButton playPauseButton;
	/**
	 * 新しい {@link MidiEditor} を構築します。
	 * @param deviceModelList MIDIデバイスモデルリスト
	 */
	public MidiEditor(MidiSequencerModel sequencerModel) {
		MidiSequenceTableModel emptyTrackTableModel = new MidiSequenceTableModel(
			sequenceListTableModel = new SequenceListTableModel(
				this.sequencerModel = sequencerModel
			)
		);
		setTitle("MIDI Editor/Playlist - MIDI Chord Helper");
		setBounds( 150, 200, 850, 500 );
		setLayout(new FlowLayout());
		new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, this, true);
		playPauseButton = new JToggleButton(sequencerModel.startStopAction);
		sequenceListTableView = new JTable(sequenceListTableModel);
		trackListTableView = new JTable(emptyTrackTableModel);
		eventListTableView = new JTable(new MidiTrackTableModel());
		//
		sequenceListTableModel.sizeColumnWidthToFit(sequenceListTableView);
		emptyTrackTableModel.sizeColumnWidthToFit(trackListTableView.getColumnModel());
		//
		seqSelectionModel = sequenceListTableView.getSelectionModel();
		seqSelectionModel.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
		seqSelectionModel.addListSelectionListener(
			new ListSelectionListener() {
				public void valueChanged(ListSelectionEvent e) {
					if( e.getValueIsAdjusting() ) return;
					sequenceSelectionChanged();
					trackSelectionModel.setSelectionInterval(0,0);
				}
			}
		);
		//
		trackSelectionModel = trackListTableView.getSelectionModel();
		trackSelectionModel.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		trackSelectionModel.addListSelectionListener(this);
		//
		eventSelectionModel = eventListTableView.getSelectionModel();
		eventSelectionModel.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		eventSelectionModel.addListSelectionListener(this);
		//
		try {
			fileChooser = new JFileChooser();
			FileNameExtensionFilter filter = new FileNameExtensionFilter(
				"MIDI sequence (*.mid)", "mid"
			);
			fileChooser.setFileFilter(filter);
		}
		catch( ExceptionInInitializerError|NoClassDefFoundError|AccessControlException e ) {
			// アプレットの場合、Webクライアントマシンのローカルファイルには
			// アクセスできないので、ファイル選択ダイアログは使用不可。
			fileChooser = null;
		}
		if( fileChooser != null ) {
			addMidiFileButton = new JButton("Open") {{
				setMargin(ZERO_INSETS);
				addActionListener(
					new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							if(
								fileChooser == null ||
								fileChooser.showOpenDialog(MidiEditor.this) != JFileChooser.APPROVE_OPTION
							) return;
							addSequenceFromMidiFile(fileChooser.getSelectedFile());
						}
					}
				);
			}};
			saveMidiFileButton = new JButton("Save") {{
				setMargin(ZERO_INSETS);
				addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						if( fileChooser == null )
							return;
						MidiSequenceTableModel sequenceTableModel =
							sequenceListTableModel.getSequenceModel(seqSelectionModel);
						String filename = sequenceTableModel.getFilename();
						File midiFile;
						if( filename != null && ! filename.isEmpty() ) {
							midiFile = new File(filename);
							fileChooser.setSelectedFile(midiFile);
						}
						if( fileChooser.showSaveDialog(MidiEditor.this) != JFileChooser.APPROVE_OPTION ) {
							return;
						}
						midiFile = fileChooser.getSelectedFile();
						if( midiFile.exists() && ! confirm(
								"Overwrite " + midiFile.getName() + " ?\n"
									+ midiFile.getName() + " を上書きしてよろしいですか？"
								) ) {
							return;
						}
						try ( FileOutputStream fos = new FileOutputStream(midiFile) ) {
							fos.write(sequenceTableModel.getMIDIdata());
							sequenceTableModel.setModified(false);
						}
						catch( IOException ex ) {
							showError( ex.getMessage() );
							ex.printStackTrace();
						}
					}
				});
			}};
		}
		Icon deleteIcon = new ButtonIcon(ButtonIcon.X_ICON);
		deleteSequenceButton = new JButton("Delete", deleteIcon) {{
			setMargin(ZERO_INSETS);
			addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if( saveMidiFileButton != null ) {
						// ファイルに保存できる場合（Javaアプレットではなく、Javaアプリとして動作している場合）
						MidiSequenceTableModel seqModel = sequenceListTableModel.getSequenceModel(seqSelectionModel);
						if( seqModel.isModified() ) {
							// ファイル未保存の変更がある場合
							String confirmMessage =
								"Selected MIDI sequence not saved - delete it ?\n" +
								"選択したMIDIシーケンスは保存されていませんが、削除しますか？";
							if( ! confirm(confirmMessage) ) {
								// ユーザに確認してNoって言われた場合
								return;
							}
						}
					}
					// 削除を実行
					sequenceListTableModel.removeSequence(seqSelectionModel);
				}
			});
		}};
		JPanel playlistPanel = new JPanel() {{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			add(new JScrollPane(sequenceListTableView));
			add(Box.createRigidArea(new Dimension(0, 10)));
			add(new JPanel() {{
				setLayout( new BoxLayout(this, BoxLayout.LINE_AXIS ));
				add(new TotalTimeLabel(sequenceListTableModel));
				add(Box.createRigidArea(new Dimension(10, 0)));
				add(new JButton("New") {{
					setToolTipText("Generate new song - 新しい曲を生成");
					setMargin(ZERO_INSETS);
					addActionListener(
						new ActionListener() {
							public void actionPerformed(ActionEvent e) {
								newSequenceDialog.setVisible(true);
							}
						}
					);
				}});
				if( addMidiFileButton != null ) {
					add( Box.createRigidArea(new Dimension(5, 0)) );
					add( addMidiFileButton );
				}
				add( Box.createRigidArea(new Dimension(5, 0)) );
				add( new JButton(moveToTopAction) {{setMargin(ZERO_INSETS);}} );
				add( Box.createRigidArea(new Dimension(5, 0)) );
				add( playPauseButton );
				add( Box.createRigidArea(new Dimension(5, 0)) );
				add( new JButton(moveToBottomAction) {{setMargin(ZERO_INSETS);}} );
				add( Box.createRigidArea(new Dimension(5, 0)) );
				add( jumpSequenceButton );
				if( saveMidiFileButton != null ) {
					add( Box.createRigidArea(new Dimension(5, 0)) );
					add( saveMidiFileButton );
				}
				if( base64Dialog.isBase64Available() ) {
					add( Box.createRigidArea(new Dimension(5, 0)) );
					add( base64EncodeButton = new JButton("Base64 Encode") {{
						setMargin(ZERO_INSETS);
						addActionListener(
							new ActionListener() {
								public void actionPerformed(ActionEvent e) {
									MidiSequenceTableModel mstm = sequenceListTableModel.getSequenceModel(seqSelectionModel);
									base64Dialog.setMIDIData(mstm.getMIDIdata(), mstm.getFilename());
									base64Dialog.setVisible(true);
								}
							}
						);
					}});
				}
				add( Box.createRigidArea(new Dimension(5, 0)) );
				add( deleteSequenceButton );
				add( Box.createRigidArea(new Dimension(5, 0)) );
				add( new SequencerSpeedSlider(
					MidiEditor.this.sequencerModel.speedSliderModel)
				);
			}});
			add( Box.createRigidArea(new Dimension(0, 10)) );
		}};
		removeTrackButton = new JButton("Delete", deleteIcon) {{
			setMargin(ZERO_INSETS);
			addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						if( ! confirm("Do you want to delete selected track ?\n選択したトラックを削除しますか？"))
							return;
						sequenceListTableModel.getSequenceModel(seqSelectionModel).deleteTracks(trackSelectionModel);
						sequenceListTableModel.fireSequenceChanged(seqSelectionModel);
					}
				}
			);
		}};
		JPanel trackListPanel = new JPanel() {{
			setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
			add(tracksLabel);
			add(Box.createRigidArea(new Dimension(0, 5)));
			add(new JScrollPane(trackListTableView));
			add(Box.createRigidArea(new Dimension(0, 5)));
			add(new JPanel() {{
				add(addTrackButton);
				add(removeTrackButton);
			}});
		}};
		removeEventButton = new JButton("Delete", deleteIcon) {{
			setMargin(ZERO_INSETS);
			addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						if( ! confirm("Do you want to delete selected event ?\n選択したMIDIイベントを削除しますか？"))
							return;
						((MidiTrackTableModel)eventListTableView.getModel()).removeMidiEvents(eventSelectionModel);
						sequenceListTableModel.fireSequenceChanged(seqSelectionModel);
					}
				}
			);
		}};
		JPanel eventListPanel = new JPanel() {{
			add(midiEventsLabel);
			add(scrollableEventTableView = new JScrollPane(eventListTableView));
			add(new JPanel() {{
				add(
					pairNoteCheckbox = new JCheckBox("Pair NoteON/OFF") {{
						setModel(eventCellEditor.pairNoteOnOffModel);
					}}
				);
				add(jumpEventButton);
				add(addEventButton);
				add(copyEventButton);
				add(cutEventButton);
				add(pasteEventButton);
				add(removeEventButton);
			}});
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
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
		seqSelectionModel.setSelectionInterval(0,0);
		sequenceSelectionChanged();
	}
	public void dragEnter(DropTargetDragEvent event) {
		if( event.isDataFlavorSupported(DataFlavor.javaFileListFlavor) )
			event.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
	}
	public void dragExit(DropTargetEvent event) {}
	public void dragOver(DropTargetDragEvent event) {}
	public void dropActionChanged(DropTargetDragEvent event) {}
	@SuppressWarnings("unchecked")
	public void drop(DropTargetDropEvent event) {
		event.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
		try {
			int action = event.getDropAction();
			if ( (action & DnDConstants.ACTION_COPY_OR_MOVE) != 0 ) {
				Transferable t = event.getTransferable();
				Object data = t.getTransferData(DataFlavor.javaFileListFlavor);
				loadAndPlay((java.util.List<File>)data);
				event.dropComplete(true);
				return;
			}
			event.dropComplete(false);
		}
		catch (Exception ex) {
			ex.printStackTrace();
			event.dropComplete(false);
		}
	}
	public void valueChanged(ListSelectionEvent e) {
		boolean is_adjusting = e.getValueIsAdjusting();
		if( is_adjusting ) return;
		Object src = e.getSource();
		if( src == trackSelectionModel ) {
			if(
				sequenceListTableModel.getSequenceModel(seqSelectionModel) == null
				||
				trackSelectionModel.isSelectionEmpty()
			) {
				midiEventsLabel.setText("MIDI Events (No track selected)");
				eventListTableView.setModel(new MidiTrackTableModel());
			}
			else {
				int sel_index = trackSelectionModel.getMinSelectionIndex();
				MidiTrackTableModel track_model
				= sequenceListTableModel.getSequenceModel(seqSelectionModel).getTrackModel(sel_index);
				if( track_model == null ) {
					midiEventsLabel.setText("MIDI Events (No track selected)");
					eventListTableView.setModel(new MidiTrackTableModel());
				}
				else {
					midiEventsLabel.setText(
						String.format("MIDI Events (in track No.%d)", sel_index)
					);
					eventListTableView.setModel(track_model);
					TableColumnModel tcm = eventListTableView.getColumnModel();
					track_model.sizeColumnWidthToFit(tcm);
					tcm.getColumn( MidiTrackTableModel.COLUMN_MESSAGE ).setCellEditor(eventCellEditor);
				}
			}
			updateButtonStatus();
			eventSelectionModel.setSelectionInterval(0,0);
		}
		else if( src == eventSelectionModel ) {
			if( ! eventSelectionModel.isSelectionEmpty() ) {
				MidiTrackTableModel track_model
				= (MidiTrackTableModel)eventListTableView.getModel();
				int min_index = eventSelectionModel.getMinSelectionIndex();
				if( track_model.hasTrack() ) {
					MidiEvent midi_event = track_model.getMidiEvent(min_index);
					MidiMessage msg = midi_event.getMessage();
					if( msg instanceof ShortMessage ) {
						ShortMessage sm = (ShortMessage)msg;
						int cmd = sm.getCommand();
						if( cmd == 0x80 || cmd == 0x90 || cmd == 0xA0 ) {
							// ノート番号を持つ場合、音を鳴らす。
							MidiChannel out_midi_channels[] = virtualMidiDevice.getChannels();
							int ch = sm.getChannel();
							int note = sm.getData1();
							int vel = sm.getData2();
							out_midi_channels[ch].noteOn( note, vel );
							out_midi_channels[ch].noteOff( note, vel );
						}
					}
				}
				if( pairNoteCheckbox.isSelected() ) {
					int max_index = eventSelectionModel.getMaxSelectionIndex();
					int partner_index;
					for( int i=min_index; i<=max_index; i++ ) {
						if(
							eventSelectionModel.isSelectedIndex(i)
							&&
							(partner_index = track_model.getIndexOfPartnerFor(i)) >= 0
							&&
							! eventSelectionModel.isSelectedIndex(partner_index)
						) {
							eventSelectionModel.addSelectionInterval(
								partner_index, partner_index
							);
						}
					}
				}
			}
			updateButtonStatus();
		}
	}
	private void showError( String message ) {
		JOptionPane.showMessageDialog(
			this, message,
			ChordHelperApplet.VersionInfo.NAME,
			JOptionPane.ERROR_MESSAGE
		);
	}
	private void showWarning( String message ) {
		JOptionPane.showMessageDialog(
			this, message,
			ChordHelperApplet.VersionInfo.NAME,
			JOptionPane.WARNING_MESSAGE
		);
	}
	private boolean confirm( String message ) {
		return JOptionPane.showConfirmDialog(
			this, message,
			ChordHelperApplet.VersionInfo.NAME,
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE
		) == JOptionPane.YES_OPTION ;
	}
	@Override
	public void setVisible(boolean isToVisible) {
		if( isToVisible && isVisible() )
			toFront();
		else
			super.setVisible(isToVisible);
	}
	@Override
	public void actionPerformed(ActionEvent e) {
		setVisible(true);
	}
	/**
	 * 選択されたシーケンスが変わったことを通知します。
	 */
	public void sequenceSelectionChanged() {
		MidiSequenceTableModel sequenceTableModel = sequenceListTableModel.getSequenceModel(seqSelectionModel);
		boolean loaded = (sequenceTableModel != null);
		if(saveMidiFileButton != null) saveMidiFileButton.setEnabled(loaded);
		if(base64EncodeButton != null) base64EncodeButton.setEnabled(loaded);
		deleteSequenceButton.setEnabled(loaded);
		jumpSequenceButton.setEnabled(loaded);
		addTrackButton.setEnabled(loaded);
		if(loaded) {
			int selectedIndex = seqSelectionModel.getMinSelectionIndex();
			trackListTableView.setModel(sequenceTableModel);
			TableColumnModel tcm = trackListTableView.getColumnModel();
			sequenceTableModel.sizeColumnWidthToFit(tcm);
			tcm.getColumn(
				MidiSequenceTableModel.Column.RECORD_CHANNEL.ordinal()
			).setCellEditor(
				sequenceTableModel.new RecordChannelCellEditor()
			);
			trackSelectionModel.setSelectionInterval(0,0);
			tracksLabel.setText(String.format("Tracks (in MIDI file No.%d)", selectedIndex));
		}
		else {
			trackListTableView.setModel(new MidiSequenceTableModel(sequenceListTableModel));
			tracksLabel.setText("Tracks (No MIDI file selected)");
		}
		updateButtonStatus();
	}
	/**
	 * ボタン状態の更新
	 */
	public void updateButtonStatus() {
		boolean isTrackSelected = (
			! trackSelectionModel.isSelectionEmpty() &&
			sequenceListTableModel.getSequenceModel(seqSelectionModel) != null &&
			sequenceListTableModel.getSequenceModel(seqSelectionModel).getRowCount() > 0
		);
		removeTrackButton.setEnabled(isTrackSelected);
		TableModel tm = eventListTableView.getModel();
		if( ! (tm instanceof MidiTrackTableModel) )
			return;
		MidiTrackTableModel trackTableModel = (MidiTrackTableModel)tm;
		jumpSequenceButton.setEnabled(
			trackTableModel != null && trackTableModel.getRowCount() > 0
		);
		boolean isEventSelected = (
			!(
				eventSelectionModel.isSelectionEmpty() ||
				trackTableModel == null || trackTableModel.getRowCount() == 0
			) && isTrackSelected
		);
		copyEventButton.setEnabled(isEventSelected);
		removeEventButton.setEnabled(isEventSelected);
		cutEventButton.setEnabled(isEventSelected);
		jumpEventButton.setEnabled(trackTableModel != null && isTrackSelected);
		addEventButton.setEnabled(trackTableModel != null && isTrackSelected);
		pasteEventButton.setEnabled(
			trackTableModel != null && isTrackSelected &&
			copiedEventsToPaste != null && copiedEventsToPaste.length > 0
		);
	}
	public String getMIDIdataBase64() {
		base64Dialog.setMIDIData(sequencerModel.getSequenceTableModel().getMIDIdata());
		return base64Dialog.getBase64Data();
	}
	/**
	 * MIDIシーケンスを追加します。
	 * シーケンサーが停止中の場合、追加したシーケンスから再生を開始します。
	 * @param seq MIDIシーケンス
	 * @return 追加先インデックス（先頭が 0）
	 */
	public int addSequence(Sequence seq) {
		int lastIndex = sequenceListTableModel.addSequence(seq);
		if( ! sequencerModel.getSequencer().isRunning() ) {
			loadAndPlay(lastIndex);
		}
		return lastIndex;
	}
	/**
	 * Base64テキストとファイル名からMIDIシーケンスを追加します。
	 * @param base64EncodedText Base64エンコードされたテキスト
	 * @param filename ファイル名
	 * @return 追加先インデックス（先頭が 0、失敗した場合は -1）
	 */
	public int addSequenceFromBase64Text(String base64EncodedText, String filename) {
		base64Dialog.setBase64Data(base64EncodedText);
		return addSequenceFromMidiData(base64Dialog.getMIDIData(), filename);
	}
	/**
	 * バイト列とファイル名からMIDIシーケンスを追加します。
	 * @param data バイト列
	 * @param filename ファイル名
	 * @return 追加先インデックス（先頭が 0、失敗した場合は -1）
	 */
	public int addSequenceFromMidiData(byte[] data, String filename) {
		int lastIndex;
		try {
			lastIndex = sequenceListTableModel.addSequence(data,filename);
		} catch( InvalidMidiDataException e ) {
			showWarning("MIDI data invalid");
			return -1;
		}
		return lastIndex;
	}
	/**
	 * MIDIファイルから読み込んだシーケンスを追加します。
	 * @param midiFile MIDIファイル
	 * @return 追加先インデックス（先頭が 0、失敗した場合は -1）
	 */
	public int addSequenceFromMidiFile(File midiFile) {
		int lastIndex;
		try {
			lastIndex = sequenceListTableModel.addSequence(midiFile);
		} catch( FileNotFoundException|InvalidMidiDataException e ) {
			showWarning(e.getMessage());
			return -1;
		} catch( AccessControlException e ) {
			showError(e.getMessage());
			e.printStackTrace();
			return -1;
		}
		return lastIndex;
	}
	/**
	 * URLから読み込んだMIDIシーケンスを追加します。
	 * @param midiFileUrl MIDIファイルのURL
	 * @return 追加先インデックス（先頭が 0、失敗した場合は -1）
	 */
	public int addSequenceFromURL(String midiFileUrl) {
		int lastIndex;
		try {
			lastIndex = sequenceListTableModel.addSequence(midiFileUrl);
		} catch( InvalidMidiDataException e ) {
			showWarning(e.getMessage());
			return -1;
		} catch( AccessControlException e ) {
			showError(e.getMessage());
			e.printStackTrace();
			return -1;
		}
		return lastIndex;
	}
	/**
	 * 指定のインデックス位置にあるMIDIシーケンスをシーケンサーにロードします。
	 * @param index MIDIシーケンスのインデックス（先頭が 0）
	 */
	public void load(int index) {
		sequenceListTableModel.loadToSequencer(index);
		sequenceSelectionChanged();
	}
	/**
	 * 次の曲をロードします。
	 * @param offset 何曲次へ進むかを表す数値
	 * @return 成功したらtrue
	 */
	public boolean loadNext(int offset) {
		boolean retval = sequenceListTableModel.loadNext(offset);
		sequenceSelectionChanged();
		return retval;
	}
	/**
	 * 選択中のMIDIシーケンスをシーケンサーにロードし、再生します。
	 */
	public void loadAndPlay() {
		loadAndPlay(seqSelectionModel.getMinSelectionIndex());
	}
	/**
	 * 指定のインデックス位置にあるMIDIシーケンスをシーケンサーにロードし、
	 * 再生します。
	 * @param index MIDIシーケンスのインデックス（先頭が 0）
	 */
	public void loadAndPlay(int index) {
		load(index);
		sequencerModel.start();
	}
	/**
	 * 複数のMIDIファイルを読み込み、再生されていなかったら再生します。
	 * @param fileList 読み込むMIDIファイルのリスト
	 */
	public void loadAndPlay(List<File> fileList) {
		int nextIndex = -1;
		for( File f : fileList ) {
			int lastIndex = addSequenceFromMidiFile(f);
			if( nextIndex == -1 )
				nextIndex = lastIndex;
		}
		if(sequencerModel.getSequencer().isRunning()) {
			setVisible(true);
		}
		else if( nextIndex >= 0 ) {
			loadAndPlay(nextIndex);
		}
	}
	/**
	 * 選択されているシーケンスが、
	 * ユーザ操作により録音可能な設定になったかどうか調べます。
	 * @return 選択されているシーケンスが録音可能な設定ならtrue
	 */
	public boolean isRecordable() {
		MidiSequenceTableModel sequenceTableModel =
			sequenceListTableModel.getSequenceModel(seqSelectionModel);
		return sequenceTableModel == null ? false : sequenceTableModel.isRecordable();
	}
	/**
	 * 指定の MIDI tick のイベントへスクロールします。
	 * @param tick MIDI tick
	 */
	public void scrollToEventAt(long tick) {
		MidiTrackTableModel trackModel = (MidiTrackTableModel)eventListTableView.getModel();
		int index = trackModel.tickToIndex(tick);
		scrollableEventTableView.getVerticalScrollBar().setValue(
			index * eventListTableView.getRowHeight()
		);
		eventSelectionModel.setSelectionInterval(index, index);
	}
}

/**
 * シーケンサーの再生スピード調整スライダビュー
 */
class SequencerSpeedSlider extends JPanel {
	private static final String items[] = {
		"x 1.0",
		"x 1.5",
		"x 2",
		"x 4",
		"x 8",
		"x 16",
	};
	private JLabel titleLabel = new JLabel("Speed:");
	private JSlider slider;
	private JComboBox<String> scaleComboBox = new JComboBox<String>(items) {{
		addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				int index = getSelectedIndex();
				BoundedRangeModel model = slider.getModel();
				if( index == 0 ) {
					model.setValue(0);
					slider.setVisible(false);
					titleLabel.setVisible(true);
				}
				else {
					int maxValue = ( index == 1 ? 7 : (index-1)*12 );
					model.setMinimum(-maxValue);
					model.setMaximum(maxValue);
					slider.setMajorTickSpacing( index == 1 ? 7 : 12 );
					slider.setMinorTickSpacing( index > 3 ? 12 : 1 );
					slider.setVisible(true);
					titleLabel.setVisible(false);
				}
			}
		});
	}};
	public SequencerSpeedSlider(BoundedRangeModel model) {
		add(titleLabel);
		add(slider = new JSlider(model){{
			setPaintTicks(true);
			setMajorTickSpacing(12);
			setMinorTickSpacing(1);
			setVisible(false);
		}});
		add(scaleComboBox);
	}
}

/**
 * プレイリスト（MIDIシーケンスリスト）のテーブルモデル
 */
class SequenceListTableModel extends AbstractTableModel implements ChangeListener {
	/**
	 * 列の列挙型
	 */
	public enum Column {
		/** MIDIシーケンスの番号 */
		SEQ_NUMBER("No.", 2, Integer.class),
		/** 変更済みフラグ */
		MODIFIED("Modified", 6, Boolean.class),
		/** タイミング分割形式 */
		DIVISION_TYPE("DivType", 6, String.class),
		/** タイミング解像度 */
		RESOLUTION("Resolution", 6, Integer.class),
		/** トラック数 */
		TRACKS("Tracks", 6, Integer.class),
		/** 再生中の時間位置（分：秒） */
		SEQ_POSITION("Position", 6, String.class),
		/** シーケンスの時間長（分：秒） */
		SEQ_LENGTH("Length", 6, String.class),
		/** ファイル名 */
		FILENAME("Filename", 16, String.class),
		/** シーケンス名（最初のトラックの名前） */
		SEQ_NAME("Sequence name", 40, String.class);
		private String title;
		private int widthRatio;
		private Class<?> columnClass;
		/**
		 * 列の識別子を構築します。
		 * @param title 列のタイトル
		 * @param widthRatio 幅の割合
		 * @param columnClass 列のクラス
		 */
		private Column(String title, int widthRatio, Class<?> columnClass) {
			this.title = title;
			this.widthRatio = widthRatio;
			this.columnClass = columnClass;
		}
		/**
		 * 幅の割合の合計を返します。
		 * @return 幅の割合の合計
		 */
		public static int totalWidthRatio() {
			int total = 0;
			for( Column c : values() ) total += c.widthRatio;
			return total;
		}
	}
	MidiSequencerModel sequencerModel;
	/**
	 * 新しいプレイリストのテーブルモデルを構築します。
	 * @param deviceManager MIDIデバイスマネージャ
	 */
	public SequenceListTableModel(MidiSequencerModel sequencerModel) {
		(this.sequencerModel = sequencerModel).addChangeListener(this);
	}
	private int secondPosition = 0;
	/**
	 * 再生中のシーケンサの秒位置が変わったときに表示を更新します。
	 */
	@Override
	public void stateChanged(ChangeEvent e) {
		int sec = sequencerModel.getValue() / 1000;
		if( secondPosition == sec )
			return;
		secondPosition = sec;
		fireTableCellUpdated(getLoadedIndex(), Column.SEQ_POSITION.ordinal());
	}
	private List<MidiSequenceTableModel> sequenceList = new ArrayList<>();
	@Override
	public int getRowCount() {
		return sequenceList.size();
	}
	@Override
	public int getColumnCount() {
		return Column.values().length;
	}
	@Override
	public String getColumnName(int column) {
		return Column.values()[column].title;
	}
	@Override
	public Class<?> getColumnClass(int column) {
		return Column.values()[column].columnClass;
	}
	@Override
	public Object getValueAt(int row, int column) {
		switch(Column.values()[column]) {
		case SEQ_NUMBER: return row;
		case MODIFIED: return sequenceList.get(row).isModified();
		case DIVISION_TYPE: {
			float divType = sequenceList.get(row).getSequence().getDivisionType();
			if( divType == Sequence.PPQ ) return "PPQ";
			else if( divType == Sequence.SMPTE_24 ) return "SMPTE_24";
			else if( divType == Sequence.SMPTE_25 ) return "SMPTE_25";
			else if( divType == Sequence.SMPTE_30 ) return "SMPTE_30";
			else if( divType == Sequence.SMPTE_30DROP ) return "SMPTE_30DROP";
			else return "[Unknown]";
		}
		case RESOLUTION:
			return sequenceList.get(row).getSequence().getResolution();
		case TRACKS:
			return sequenceList.get(row).getSequence().getTracks().length;
		case SEQ_POSITION: {
			Sequence loadedSequence = sequencerModel.getSequencer().getSequence();
			if( loadedSequence != null && loadedSequence == sequenceList.get(row).getSequence() )
				return String.format("%02d:%02d", secondPosition/60, secondPosition%60);
			else
				return "";
		}
		case SEQ_LENGTH: {
			long usec = sequenceList.get(row).getSequence().getMicrosecondLength();
			int sec = (int)( (usec < 0 ? usec += 0x100000000L : usec) / 1000L / 1000L );
			return String.format( "%02d:%02d", sec/60, sec%60 );
		}
		case FILENAME: {
			String filename = sequenceList.get(row).getFilename();
			return filename == null ? "" : filename;
		}
		case SEQ_NAME: {
			String name = sequenceList.get(row).toString();
			return name == null ? "" : name;
		}
		default: return "";
		}
	}
	public boolean isCellEditable( int row, int column ) {
		Column c = Column.values()[column];
		return c == Column.FILENAME || c == Column.SEQ_NAME ;
	}
	public void setValueAt(Object val, int row, int column) {
		switch(Column.values()[column]) {
		case FILENAME:
			// ファイル名の変更
			String filename = (String)val;
			sequenceList.get(row).setFilename(filename);
			fireTableCellUpdated(row, column);
			break;
		case SEQ_NAME:
			// シーケンス名の設定または変更
			if( sequenceList.get(row).setName((String)val) )
				fireTableCellUpdated(row, Column.MODIFIED.ordinal());
			break;
		default:
			break;
		}
	}
	/**
	 * 列に合わせて幅を調整します。
	 * @param tableView テーブルビュー
	 */
	public void sizeColumnWidthToFit(JTable tableView) {
		TableColumnModel columnModel = tableView.getColumnModel();
		int totalWidth = columnModel.getTotalColumnWidth();
		int totalWidthRatio = Column.totalWidthRatio();
		for( Column c : Column.values() ) {
			int w = totalWidth * c.widthRatio / totalWidthRatio;
			columnModel.getColumn(c.ordinal()).setPreferredWidth(w);
		}
	}
	/**
	 * このプレイリストに読み込まれた全シーケンスの合計時間長を返します。
	 * @return 全シーケンスの合計時間長 [秒]
	 */
	public int getTotalSeconds() {
		int total = 0;
		long usec;
		for( MidiSequenceTableModel m : sequenceList ) {
			usec = m.getSequence().getMicrosecondLength();
			total += (int)( (usec < 0 ? usec += 0x100000000L : usec)/1000L/1000L );
		}
		return total;
	}
	/**
	 * 未保存の修正内容を持つシーケンスがあるか調べます。
	 * @return 未保存の修正内容を持つシーケンスがあればtrue
	 */
	public boolean isModified() {
		for( MidiSequenceTableModel m : sequenceList ) {
			if( m.isModified() ) return true;
		}
		return false;
	}
	/**
	 * 選択したシーケンスに未保存の修正内容があることを記録します。
	 * @param selModel 選択状態
	 * @param isModified 未保存の修正内容があるときtrue
	 */
	public void setModified(ListSelectionModel selModel, boolean isModified) {
		int minIndex = selModel.getMinSelectionIndex();
		int maxIndex = selModel.getMaxSelectionIndex();
		for( int i = minIndex; i <= maxIndex; i++ ) {
			if( selModel.isSelectedIndex(i) ) {
				sequenceList.get(i).setModified(isModified);
				fireTableCellUpdated(i, Column.MODIFIED.ordinal());
			}
		}
	}
	/**
	 * 選択されたMIDIシーケンスのテーブルモデルを返します。
	 * @param selectionModel 選択状態
	 * @return 選択されたMIDIシーケンスのテーブルモデル
	 */
	public MidiSequenceTableModel getSequenceModel(ListSelectionModel selectionModel) {
		if( selectionModel.isSelectionEmpty() )
			return null;
		int selectedIndex = selectionModel.getMinSelectionIndex();
		if( selectedIndex >= sequenceList.size() )
			return null;
		return sequenceList.get(selectedIndex);
	}
	public void fireSequenceChanged(ListSelectionModel selectionModel) {
		if( ! selectionModel.isSelectionEmpty() ) fireSequenceChanged(
			selectionModel.getMinSelectionIndex(),
			selectionModel.getMaxSelectionIndex()
		);
	}
	public void fireSequenceChanged(MidiSequenceTableModel sequenceTableModel) {
		int index = sequenceList.indexOf(sequenceTableModel);
		if( index < 0 ) return;
		fireSequenceChanged(index,index);
	}
	public void fireSequenceChanged(int minIndex, int maxIndex) {
		for( int index = minIndex; index <= maxIndex; index++ ) {
			MidiSequenceTableModel model = sequenceList.get(index);
			model.setModified(true);
			if( sequencerModel.getSequencer().getSequence() == model.getSequence() ) {
				// シーケンサーに対して、同じシーケンスを再度セットする。
				// （これをやらないと更新が反映されないため）
				sequencerModel.setSequenceTableModel(model);
			}
		}
		fireTableRowsUpdated(minIndex, maxIndex);
	}
	public int addSequence() {
		Sequence seq = (new Music.ChordProgression()).toMidiSequence();
		return seq == null ? -1 : addSequence(seq,null);
	}
	public int addSequence(Sequence seq) {
		return addSequence(seq, "");
	}
	public int addSequence(Sequence seq, String filename) {
		MidiSequenceTableModel seqModel = new MidiSequenceTableModel(this);
		seqModel.setSequence(seq);
		seqModel.setFilename(filename);
		sequenceList.add(seqModel);
		int lastIndex = sequenceList.size() - 1;
		fireTableRowsInserted(lastIndex, lastIndex);
		return lastIndex;
	}
	public int addSequence(byte[] midiData, String filename) throws InvalidMidiDataException {
		return (midiData == null) ? addSequence() : addSequence(new ByteArrayInputStream(midiData), filename);
	}
	public int addSequence(File midiFile) throws InvalidMidiDataException, FileNotFoundException {
		FileInputStream fis = new FileInputStream(midiFile);
		int retval = addSequence(fis, midiFile.getName());
		try {
			fis.close();
		} catch( IOException ex ) {
			ex.printStackTrace();
		}
		return retval;
	}
	public int addSequence(InputStream in, String filename) throws InvalidMidiDataException {
		if(in == null) return addSequence();
		Sequence seq;
		try {
			seq = MidiSystem.getSequence(in);
		} catch ( InvalidMidiDataException e ) {
			throw e;
		} catch ( EOFException e ) {
			// No MIDI data
			return -1;
		} catch ( IOException e ) {
			e.printStackTrace();
			return -1;
		}
		return addSequence( seq, filename );
	}
	public int addSequence(String midiFileUrl) throws InvalidMidiDataException, AccessControlException {
		URL url = toURL(midiFileUrl);
		if( url == null ) {
			return -1;
		}
		Sequence seq;
		try {
			seq = MidiSystem.getSequence(url);
		} catch ( InvalidMidiDataException e ) {
			throw e;
		} catch( EOFException e ) {
			// No MIDI data
			return -1;
		} catch( IOException e ) {
			e.printStackTrace();
			return -1;
		}
		return addSequence(seq, url.getFile().replaceFirst("^.*/",""));
	}
	/**
	 * 選択したシーケンスを除去します。
	 * @param listSelectionModel 選択状態
	 */
	public void removeSequence(ListSelectionModel listSelectionModel) {
		if( listSelectionModel.isSelectionEmpty() )
			return;
		int selectedIndex = listSelectionModel.getMinSelectionIndex();
		if(sequenceList.get(selectedIndex) == sequencerModel.getSequenceTableModel())
			sequencerModel.setSequenceTableModel(null);
		sequenceList.remove(selectedIndex);
		fireTableRowsDeleted(selectedIndex, selectedIndex);
	}
	/**
	 * 指定したインデックス位置のシーケンスをシーケンサーにロードします。
	 * @param index シーケンスのインデックス位置
	 */
	public void loadToSequencer(int index) {
		int oldIndex = getLoadedIndex();
		if(index == oldIndex)
			return;
		MidiSequenceTableModel sequenceTableModel = sequenceList.get(index);
		sequencerModel.setSequenceTableModel(sequenceTableModel);
		//
		// 表示更新のための変更通知
		sequenceTableModel.fireTableDataChanged();
		int columnIndex = Column.SEQ_POSITION.ordinal();
		fireTableCellUpdated(oldIndex, columnIndex);
		fireTableCellUpdated(index, columnIndex);
	}
	/**
	 * 現在シーケンサにロードされているシーケンスのインデックスを返します。
	 * ロードされていない場合は -1 を返します。
	 * @return 現在シーケンサにロードされているシーケンスのインデックス
	 */
	public int getLoadedIndex() {
		return sequenceList.indexOf(sequencerModel.getSequenceTableModel());
	}
	/**
	 * 引数で示された数だけ次へ進めたシーケンスをロードします。
	 * @param offset 進みたいシーケンス数
	 * @return 成功したらtrue
	 */
	public boolean loadNext(int offset) {
		int loadedIndex = getLoadedIndex();
		int index = (loadedIndex < 0 ? 0 : loadedIndex + offset);
		if( index < 0 || index >= sequenceList.size() )
			return false;
		loadToSequencer( index );
		return true;
	}
	/**
	 * 文字列をURLオブジェクトに変換
	 * @param urlString URL文字列
	 * @return URLオブジェクト
	 */
	private URL toURL(String urlString) {
		if( urlString == null || urlString.isEmpty() ) {
			return null;
		}
		URI uri = null;
		URL url = null;
		try {
			uri = new URI(urlString);
			url = uri.toURL();
		} catch( URISyntaxException e ) {
			e.printStackTrace();
		} catch( MalformedURLException e ) {
			e.printStackTrace();
		}
		return url;
	}
}

/**
 * MIDIシーケンス（トラックリスト）を表すテーブルモデル
 */
class MidiSequenceTableModel extends AbstractTableModel {
	/**
	 * 列の列挙型
	 */
	public enum Column {
		/** トラック番号 */
		TRACK_NUMBER("No.", 30, Integer.class),
		/** イベント数 */
		EVENTS("Events", 60, Integer.class),
		/** Mute */
		MUTE("Mute", 40, Boolean.class),
		/** Solo */
		SOLO("Solo", 40, Boolean.class),
		/** 録音するMIDIチャンネル */
		RECORD_CHANNEL("RecCh", 60, String.class),
		/** MIDIチャンネル */
		CHANNEL("Ch", 60, String.class),
		/** トラック名 */
		TRACK_NAME("Track name", 200, String.class);
		private String title;
		private int widthRatio;
		private Class<?> columnClass;
		/**
		 * 列の識別子を構築します。
		 * @param title 列のタイトル
		 * @param widthRatio 幅の割合
		 * @param columnClass 列のクラス
		 */
		private Column(String title, int widthRatio, Class<?> columnClass) {
			this.title = title;
			this.widthRatio = widthRatio;
			this.columnClass = columnClass;
		}
		/**
		 * 幅の割合の合計を返します。
		 * @return 幅の割合の合計
		 */
		public static int totalWidthRatio() {
			int total = 0;
			for( Column c : values() ) total += c.widthRatio;
			return total;
		}
	}
	private SequenceListTableModel sequenceListTableModel;
	private Sequence sequence;
	private SequenceTickIndex sequenceTickIndex;
	private String filename = "";
	private List<MidiTrackTableModel> trackModelList = new ArrayList<>();
	/**
	 * 記録するMIDIチャンネル
	 */
	class RecordChannelCellEditor extends DefaultCellEditor {
		public RecordChannelCellEditor() {
			super(
				new JComboBox<String>() {
					{
						addItem("OFF");
						for( int i=1; i <= MIDISpec.MAX_CHANNELS; i++ )
							addItem( String.format("%d", i) );
						addItem("ALL");
					}
				}
			);
		}
	}
	/**
	 * 新しい {@link MidiSequenceTableModel} を構築します。
	 * @param sequenceListTableModel プレイリスト
	 */
	public MidiSequenceTableModel(SequenceListTableModel sequenceListTableModel) {
		this.sequenceListTableModel = sequenceListTableModel;
	}
	@Override
	public int getRowCount() {
		return sequence == null ? 0 : sequence.getTracks().length;
	}
	@Override
	public int getColumnCount() { return Column.values().length; }
	/**
	 * 列名を返します。
	 * @return 列名
	 */
	@Override
	public String getColumnName(int column) {
		return Column.values()[column].title;
	}
	/**
	 * 指定された列の型を返します。
	 * @return 指定された列の型
	 */
	@Override
	public Class<?> getColumnClass(int column) {
		Column c = Column.values()[column];
		switch(c) {
		case MUTE:
		case SOLO:
			if( sequence != getSequencer().getSequence() )
				return String.class;
			// FALLTHROUGH
		default:
			return c.columnClass;
		}
	}
	@Override
	public Object getValueAt(int row, int column) {
		Column c = Column.values()[column];
		switch(c) {
		case TRACK_NUMBER: return row;
		case EVENTS: return sequence.getTracks()[row].size();
		case MUTE:
			return (sequence == getSequencer().getSequence()) ?
				getSequencer().getTrackMute(row) : "";
		case SOLO:
			return (sequence == getSequencer().getSequence()) ?
				getSequencer().getTrackSolo(row) : "";
		case RECORD_CHANNEL:
			return (sequence == getSequencer().getSequence()) ?
				trackModelList.get(row).getRecordingChannel() : "";
		case CHANNEL: {
			int ch = trackModelList.get(row).getChannel();
			return ch < 0 ? "" : ch + 1 ;
		}
		case TRACK_NAME: return trackModelList.get(row).toString();
		default: return "";
		}
	}
	/**
	 * セルが編集可能かどうかを返します。
	 */
	@Override
	public boolean isCellEditable(int row, int column) {
		Column c = Column.values()[column];
		switch(c) {
		case MUTE:
		case SOLO:
		case RECORD_CHANNEL: return sequence == getSequencer().getSequence();
		case CHANNEL:
		case TRACK_NAME: return true;
		default: return false;
		}
	}
	/**
	 * 列の値を設定します。
	 */
	@Override
	public void setValueAt(Object val, int row, int column) {
		Column c = Column.values()[column];
		switch(c) {
		case MUTE:
			getSequencer().setTrackMute(row, ((Boolean)val).booleanValue());
			break;
		case SOLO:
			getSequencer().setTrackSolo(row, ((Boolean)val).booleanValue());
			break;
		case RECORD_CHANNEL:
			trackModelList.get(row).setRecordingChannel((String)val);
			break;
		case CHANNEL: {
			Integer ch;
			try {
				ch = new Integer((String)val);
			}
			catch( NumberFormatException e ) {
				ch = -1;
				break;
			}
			if( --ch <= 0 || ch > MIDISpec.MAX_CHANNELS )
				break;
			MidiTrackTableModel trackTableModel = trackModelList.get(row);
			if( ch == trackTableModel.getChannel() ) break;
			trackTableModel.setChannel(ch);
			setModified(true);
			fireTableCellUpdated(row,Column.EVENTS.ordinal());
			break;
		}
		case TRACK_NAME:
			trackModelList.get(row).setString((String)val);
			break;
		default:
			break;
		}
		fireTableCellUpdated(row,column);
	}
	/**
	 * 列に合わせて幅を調整します。
	 * @param columnModel テーブル列モデル
	 */
	public void sizeColumnWidthToFit(TableColumnModel columnModel) {
		int totalWidth = columnModel.getTotalColumnWidth();
		int totalWidthRatio = Column.totalWidthRatio();
		for( Column c : Column.values() ) {
			int w = totalWidth * c.widthRatio / totalWidthRatio;
			columnModel.getColumn(c.ordinal()).setPreferredWidth(w);
		}
	}
	/**
	 * MIDIシーケンスを返します。
	 * @return MIDIシーケンス
	 */
	public Sequence getSequence() { return sequence; }
	/**
	 * シーケンスtickインデックスを返します。
	 * @return シーケンスtickインデックス
	 */
	public SequenceTickIndex getSequenceTickIndex() {
		return sequenceTickIndex;
	}
	/**
	 * MIDIシーケンスを設定します。
	 * @param sequence MIDIシーケンス
	 */
	public void setSequence(Sequence sequence) {
		getSequencer().recordDisable(null); // The "null" means all tracks
		this.sequence = sequence;
		int oldSize = trackModelList.size();
		if( oldSize > 0 ) {
			trackModelList.clear();
			fireTableRowsDeleted(0, oldSize-1);
		}
		if( sequence == null ) {
			sequenceTickIndex = null;
		}
		else {
			fireTimeSignatureChanged();
			Track tracks[] = sequence.getTracks();
			for( Track track : tracks )
				trackModelList.add(new MidiTrackTableModel(track, this));
			fireTableRowsInserted(0, tracks.length-1);
		}
	}
	/**
	 * 拍子が変更されたとき、シーケンスtickインデックスを再作成します。
	 */
	public void fireTimeSignatureChanged() {
		sequenceTickIndex = new SequenceTickIndex(sequence);
	}
	private boolean isModified = false;
	/**
	 * 変更されたかどうかを返します。
	 * @return 変更済みのときtrue
	 */
	public boolean isModified() { return isModified; }
	/**
	 * 変更されたかどうかを設定します。
	 * @param isModified 変更されたときtrue
	 */
	public void setModified(boolean isModified) { this.isModified = isModified; }
	/**
	 * ファイル名を設定します。
	 * @param filename ファイル名
	 */
	public void setFilename(String filename) { this.filename = filename; }
	/**
	 * ファイル名を返します。
	 * @return ファイル名
	 */
	public String getFilename() { return filename; }
	@Override
	public String toString() { return MIDISpec.getNameOf(sequence); }
	/**
	 * シーケンス名を設定します。
	 * @param name シーケンス名
	 * @return 成功したらtrue
	 */
	public boolean setName(String name) {
		if( name.equals(toString()) || ! MIDISpec.setNameOf(sequence,name) )
			return false;
		setModified(true);
		fireTableDataChanged();
		return true;
	}
	/**
	 * このシーケンスのMIDIデータのバイト列を返します。
	 * @return MIDIデータのバイト列（失敗した場合null）
	 */
	public byte[] getMIDIdata() {
		if( sequence == null || sequence.getTracks().length == 0 ) {
			return null;
		}
		try( ByteArrayOutputStream out = new ByteArrayOutputStream() ) {
			MidiSystem.write(sequence, 1, out);
			return out.toByteArray();
		} catch ( IOException e ) {
			e.printStackTrace();
			return null;
		}
	}
	/**
	 * 指定のトラックが変更されたことを通知します。
	 * @param track トラック
	 */
	public void fireTrackChanged(Track track) {
		int row = indexOf(track);
		if( row < 0 ) return;
		fireTableRowsUpdated(row, row);
		fireSequenceChanged();
	}
	/**
	 * このシーケンスが変更されたことを通知します。
	 */
	public void fireSequenceChanged() {
		sequenceListTableModel.fireSequenceChanged(this);
	}
	/**
	 * 指定のインデックスのトラックモデルを返します。
	 * @param index トラックのインデックス
	 * @return トラックモデル（見つからない場合null）
	 */
	public MidiTrackTableModel getTrackModel(int index) {
		Track tracks[] = sequence.getTracks();
		if( tracks.length != 0 ) {
			Track track = tracks[index];
			for( MidiTrackTableModel model : trackModelList )
				if( model.getTrack() == track )
					return model;
		}
		return null;
	}
	/**
	 * 指定のトラックがある位置のインデックスを返します。
	 * @param track トラック
	 * @return トラックのインデックス（先頭 0、トラックが見つからない場合 -1）
	 */
	public int indexOf(Track track) {
		Track tracks[] = sequence.getTracks();
		for( int i=0; i<tracks.length; i++ )
			if( tracks[i] == track )
				return i;
		return -1;
	}
	/**
	 * 新しいトラックを生成し、末尾に追加します。
	 * @return 追加したトラックのインデックス（先頭 0）
	 */
	public int createTrack() {
		trackModelList.add(new MidiTrackTableModel(sequence.createTrack(), this));
		int lastRow = sequence.getTracks().length - 1;
		fireTableRowsInserted(lastRow, lastRow);
		return lastRow;
	}
	/**
	 * 選択されているトラックを削除します。
	 * @param selectionModel 選択状態
	 */
	public void deleteTracks(ListSelectionModel selectionModel) {
		if( selectionModel.isSelectionEmpty() )
			return;
		int minIndex = selectionModel.getMinSelectionIndex();
		int maxIndex = selectionModel.getMaxSelectionIndex();
		Track tracks[] = sequence.getTracks();
		for( int i = maxIndex; i >= minIndex; i-- ) {
			if( ! selectionModel.isSelectedIndex(i) )
				continue;
			sequence.deleteTrack(tracks[i]);
			trackModelList.remove(i);
		}
		fireTableRowsDeleted( minIndex, maxIndex );
	}
	/**
	 * MIDIシーケンサを返します。
	 * @return MIDIシーケンサ
	 */
	public Sequencer getSequencer() {
		return sequenceListTableModel.sequencerModel.getSequencer();
	}
	/**
	 * 録音可能かどうかを返します。
	 * @return 録音可能であればtrue
	 */
	public boolean isRecordable() {
		if( sequence != getSequencer().getSequence() )
			return false;
		int rowCount = getRowCount();
		int col = Column.RECORD_CHANNEL.ordinal();
		for( int row=0; row < rowCount; row++ ) {
			if("OFF".equals(getValueAt(row, col))) continue;
			return true;
		}
		return false;
	}
}

/**
 * MIDIトラック（MIDIイベントリスト）テーブルモデル
 */
class MidiTrackTableModel extends AbstractTableModel
{
	public static final int COLUMN_EVENT_NUMBER	= 0;
	public static final int COLUMN_TICK_POSITION	= 1;
	public static final int COLUMN_MEASURE_POSITION	= 2;
	public static final int COLUMN_BEAT_POSITION		= 3;
	public static final int COLUMN_EXTRA_TICK_POSITION	= 4;
	public static final int COLUMN_MESSAGE	= 5;
	public static final String COLUMN_TITLES[] = {
		"No.", "TickPos.", "Measure", "Beat", "ExTick", "MIDI Message",
	};
	public static final int column_width_ratios[] = {
		30, 40, 20,20,20, 280,
	};
	private Track track;
	private MidiSequenceTableModel parent;
	public MidiTrackTableModel() { } // To create empty model
	public MidiTrackTableModel(MidiSequenceTableModel parent) {
		this.parent = parent;
	}
	public MidiTrackTableModel(Track track, MidiSequenceTableModel parent) {
		this.track = track;
		this.parent = parent;
	}
	public int getRowCount() { return track == null ? 0 : track.size(); }
	public int getColumnCount() { return COLUMN_TITLES.length; }
	public String getColumnName(int column) { return COLUMN_TITLES[column]; }
	public Class<?> getColumnClass(int column) {
		switch(column) {
		case COLUMN_EVENT_NUMBER:
			return Integer.class;
		case COLUMN_TICK_POSITION:
			return Long.class;
		case COLUMN_MEASURE_POSITION:
		case COLUMN_BEAT_POSITION:
		case COLUMN_EXTRA_TICK_POSITION:
			return Integer.class;
			// case COLUMN_MESSAGE:
			default:
				return String.class;
		}
		// return getValueAt(0,column).getClass();
	}
	public Object getValueAt(int row, int column) {
		switch(column) {
		case COLUMN_EVENT_NUMBER:
			return row;

		case COLUMN_TICK_POSITION:
			return track.get(row).getTick();

		case COLUMN_MEASURE_POSITION:
			return parent.getSequenceTickIndex().tickToMeasure(track.get(row).getTick()) + 1;

		case COLUMN_BEAT_POSITION:
			parent.getSequenceTickIndex().tickToMeasure(track.get(row).getTick());
			return parent.getSequenceTickIndex().lastBeat + 1;

		case COLUMN_EXTRA_TICK_POSITION:
			parent.getSequenceTickIndex().tickToMeasure(track.get(row).getTick());
			return parent.getSequenceTickIndex().lastExtraTick;

		case COLUMN_MESSAGE:
			return msgToString(track.get(row).getMessage());

		default: return "";
		}
	}
	public boolean isCellEditable(int row, int column) {
		switch(column) {
		// case COLUMN_EVENT_NUMBER:
		case COLUMN_TICK_POSITION:
		case COLUMN_MEASURE_POSITION:
		case COLUMN_BEAT_POSITION:
		case COLUMN_EXTRA_TICK_POSITION:
		case COLUMN_MESSAGE:
			return true;
		default: return false;
		}
	}
	public void setValueAt(Object value, int row, int column) {
		long tick;
		switch(column) {
		// case COLUMN_EVENT_NUMBER:
		case COLUMN_TICK_POSITION:
			tick = (Long)value;
			break;
		case COLUMN_MEASURE_POSITION:
			tick = parent.getSequenceTickIndex().measureToTick(
				(Integer)value - 1,
				(Integer)getValueAt( row, COLUMN_BEAT_POSITION ) - 1,
				(Integer)getValueAt( row, COLUMN_EXTRA_TICK_POSITION )
			);
			break;
		case COLUMN_BEAT_POSITION:
			tick = parent.getSequenceTickIndex().measureToTick(
				(Integer)getValueAt( row, COLUMN_MEASURE_POSITION ) - 1,
				(Integer)value - 1,
				(Integer)getValueAt( row, COLUMN_EXTRA_TICK_POSITION )
			);
			break;
		case COLUMN_EXTRA_TICK_POSITION:
			tick = parent.getSequenceTickIndex().measureToTick(
				(Integer)getValueAt( row, COLUMN_MEASURE_POSITION ) - 1,
				(Integer)getValueAt( row, COLUMN_BEAT_POSITION ) - 1,
				(Integer)value
			);
			break;
		case COLUMN_MESSAGE:
			return;
		default: return;
		}
		changeEventTick(row,tick);
	}
	public void sizeColumnWidthToFit( TableColumnModel column_model ) {
		int total_width = column_model.getTotalColumnWidth();
		int i, total_width_ratio = 0;
		for( i=0; i<column_width_ratios.length; i++ ) {
			total_width_ratio += column_width_ratios[i];
		}
		for( i=0; i<column_width_ratios.length; i++ ) {
			column_model.getColumn(i).setPreferredWidth(
				total_width * column_width_ratios[i] / total_width_ratio
			);
		}
	}
	/**
	 * MIDIトラックを返します。
	 * @return MIDIトラック
	 */
	public Track getTrack() { return track; }
	/**
	 * 文字列としてトラック名を返します。
	 */
	@Override
	public String toString() { return MIDISpec.getNameOf(track); }
	/**
	 * トラック名を設定します。
	 * @param name トラック名
	 * @return 設定が行われたらtrue
	 */
	public boolean setString(String name) {
		if(name.equals(toString()) || ! MIDISpec.setNameOf(track, name))
			return false;
		parent.setModified(true);
		parent.fireSequenceChanged();
		fireTableDataChanged();
		return true;
	}
	private String rec_ch = "OFF";
	/**
	 * 録音中のMIDIチャンネルを返します。
	 * @return 録音中のMIDIチャンネル
	 */
	public String getRecordingChannel() { return rec_ch; }
	/**
	 * 録音中のMIDIチャンネルを設定します。
	 * @param ch_str 録音中のMIDIチャンネル
	 */
	public void setRecordingChannel(String ch_str) {
		Sequencer sequencer = parent.getSequencer();
		if( ch_str.equals("OFF") ) {
			sequencer.recordDisable( track );
		}
		else if( ch_str.equals("ALL") ) {
			sequencer.recordEnable( track, -1 );
		}
		else {
			try {
				int ch = Integer.decode(ch_str).intValue() - 1;
				sequencer.recordEnable( track, ch );
			} catch( NumberFormatException nfe ) {
				sequencer.recordDisable( track );
				rec_ch = "OFF";
				return;
			}
		}
		rec_ch = ch_str;
	}
	//
	// 対象MIDIチャンネル
	public int getChannel() {
		MidiMessage msg;
		ShortMessage smsg;
		int index, ch, prev_ch = -1, track_size = track.size();
		for( index=0; index < track_size; index++ ) {
			msg = track.get(index).getMessage();
			if( ! (msg instanceof ShortMessage) )
				continue;
			smsg = (ShortMessage)msg;
			if( ! MIDISpec.isChannelMessage(smsg) )
				continue;
			ch = smsg.getChannel();
			if( prev_ch >= 0 && prev_ch != ch ) {
				// MIDIチャンネルが統一されていない場合
				return -1;
			}
			prev_ch = ch;
		}
		// すべてのMIDIチャンネルが同じならそれを返す
		return prev_ch;
	}
	public void setChannel(int ch) {
		// すべてのチャンネルメッセージに対して
		// 同一のMIDIチャンネルをセットする
		int track_size = track.size();
		for( int index=0; index < track_size; index++ ) {
			MidiMessage msg = track.get(index).getMessage();
			if( ! (msg instanceof ShortMessage) )
				continue;
			ShortMessage smsg = (ShortMessage)msg;
			if( ! MIDISpec.isChannelMessage(smsg) )
				continue;
			if( smsg.getChannel() == ch )
				continue;
			try {
				smsg.setMessage(
					smsg.getCommand(), ch,
					smsg.getData1(), smsg.getData2()
				);
			}
			catch( InvalidMidiDataException e ) {
				e.printStackTrace();
			}
			parent.setModified(true);
		}
		parent.fireTrackChanged( track );
		parent.fireSequenceChanged();
		fireTableDataChanged();
	}
	/**
	 * MIDIイベントのtick位置を変更します。
	 * @param row 行インデックス
	 * @param new_tick 新しいtick位置
	 */
	public void changeEventTick(int row, long new_tick) {
		MidiEvent old_midi_event = track.get(row);
		if( old_midi_event.getTick() == new_tick ) {
			return;
		}
		MidiMessage msg = old_midi_event.getMessage();
		MidiEvent new_midi_event = new MidiEvent(msg,new_tick);
		track.remove(old_midi_event);
		track.add(new_midi_event);
		fireTableDataChanged();
		if( MIDISpec.isEOT(msg) ) {
			// EOTの場所が変わると曲の長さが変わるので、親モデルへ通知する。
			parent.fireSequenceChanged();
		}
	}
	/**
	 * MIDI tick を行インデックスに変換します。
	 * 検索はバイナリーサーチで行われます。
	 * @param tick MIDI tick
	 * @return 行インデックス
	 */
	public int tickToIndex( long tick ) {
		if( track == null )
			return 0;
		int min_index = 0;
		int max_index = track.size() - 1;
		long current_tick;
		int current_index;
		while( min_index < max_index ) {
			current_index = (min_index + max_index) / 2 ;
			current_tick = track.get(current_index).getTick();
			if( tick > current_tick ) {
				min_index = current_index + 1;
			}
			else if( tick < current_tick ) {
				max_index = current_index - 1;
			}
			else {
				return current_index;
			}
		}
		return (min_index + max_index) / 2;
	}
	/**
	 * NoteOn/NoteOff ペアの一方の行インデックスから、
	 * もう一方（ペアの相手）の行インデックスを返します。
	 * @param index 行インデックス
	 * @return ペアを構成する相手の行インデックス（ない場合は -1）
	 */
	public int getIndexOfPartnerFor( int index ) {
		if( track == null || index >= track.size() )
			return -1;
		MidiMessage msg = track.get(index).getMessage();
		if( ! (msg instanceof ShortMessage) ) return -1;
		ShortMessage sm = (ShortMessage)msg;
		int cmd = sm.getCommand();
		int i;
		int ch = sm.getChannel();
		int note = sm.getData1();
		MidiMessage partner_msg;
		ShortMessage partner_sm;
		int partner_cmd;

		switch( cmd ) {
		case 0x90: // NoteOn
		if( sm.getData2() > 0 ) {
			// Search NoteOff event forward
			for( i = index + 1; i < track.size(); i++ ) {
				partner_msg = track.get(i).getMessage();
				if( ! (partner_msg instanceof ShortMessage ) ) continue;
				partner_sm = (ShortMessage)partner_msg;
				partner_cmd = partner_sm.getCommand();
				if( partner_cmd != 0x80 && partner_cmd != 0x90 ||
						partner_cmd == 0x90 && partner_sm.getData2() > 0
						) {
					// Not NoteOff
					continue;
				}
				if( ch != partner_sm.getChannel() || note != partner_sm.getData1() ) {
					// Not my partner
					continue;
				}
				return i;
			}
			break;
		}
		// When velocity is 0, it means Note Off, so no break.
		case 0x80: // NoteOff
			// Search NoteOn event backward
			for( i = index - 1; i >= 0; i-- ) {
				partner_msg = track.get(i).getMessage();
				if( ! (partner_msg instanceof ShortMessage ) ) continue;
				partner_sm = (ShortMessage)partner_msg;
				partner_cmd = partner_sm.getCommand();
				if( partner_cmd != 0x90 || partner_sm.getData2() <= 0 ) {
					// Not NoteOn
					continue;
				}
				if( ch != partner_sm.getChannel() || note != partner_sm.getData1() ) {
					// Not my partner
					continue;
				}
				return i;
			}
			break;
		}
		// Not found
		return -1;
	}
	/**
	 * 指定のMIDIメッセージが拍子記号かどうか調べます。
	 * @param msg MIDIメッセージ
	 * @return 拍子記号のときtrue
	 */
	public boolean isTimeSignature( MidiMessage msg ) {
		return (msg instanceof MetaMessage) && ((MetaMessage)msg).getType() == 0x58;
	}
	/**
	 * ノートメッセージかどうか調べます。
	 * @param index 行インデックス
	 * @return Note On または Note Off のとき true
	 */
	public boolean isNote(int index) {
		MidiEvent midi_evt = getMidiEvent(index);
		MidiMessage msg = midi_evt.getMessage();
		if( ! (msg instanceof ShortMessage) ) return false;
		int cmd = ((ShortMessage)msg).getCommand();
		return cmd == ShortMessage.NOTE_ON || cmd == ShortMessage.NOTE_OFF ;
	}
	public boolean hasTrack() { return track != null; }
	/**
	 * 指定の行インデックスのMIDIイベントを返します。
	 * @param index 行インデックス
	 * @return MIDIイベント
	 */
	public MidiEvent getMidiEvent(int index) { return track.get(index); }
	/**
	 * 選択されているMIDIイベントを返します。
	 * @param selectionModel 選択状態モデル
	 * @return 選択されているMIDIイベント
	 */
	public MidiEvent[] getMidiEvents(ListSelectionModel selectionModel) {
		Vector<MidiEvent> events = new Vector<MidiEvent>();
		if( ! selectionModel.isSelectionEmpty() ) {
			int i = selectionModel.getMinSelectionIndex();
			int max = selectionModel.getMaxSelectionIndex();
			for( ; i <= max; i++ )
				if( selectionModel.isSelectedIndex(i) )
					events.add(track.get(i));
		}
		return events.toArray(new MidiEvent[1]);
	}
	/**
	 * MIDIイベントを追加します。
	 * @param midiEvent 追加するMIDIイベント
	 * @return 追加できたらtrue
	 */
	public boolean addMidiEvent(MidiEvent midiEvent) {
		if( !(track.add(midiEvent)) )
			return false;
		if( isTimeSignature(midiEvent.getMessage()) )
			parent.fireTimeSignatureChanged();
		parent.fireTrackChanged(track);
		int last_index = track.size() - 1;
		fireTableRowsInserted( last_index-1, last_index-1 );
		return true;
	}
	/**
	 * MIDIイベントを追加します。
	 * @param midiEvents 追加するMIDIイベント
	 * @param destinationTick 追加先tick
	 * @param sourcePPQ PPQ値（タイミング解像度）
	 * @return 追加できたらtrue
	 */
	public boolean addMidiEvents(MidiEvent midiEvents[], long destinationTick, int sourcePPQ) {
		int destinationPPQ = parent.getSequence().getResolution();
		boolean done = false;
		boolean hasTimeSignature = false;
		long firstSourceEventTick = -1;
		for( MidiEvent sourceEvent : midiEvents ) {
			long sourceEventTick = sourceEvent.getTick();
			MidiMessage msg = sourceEvent.getMessage();
			long newTick = destinationTick;
			if( firstSourceEventTick < 0 ) {
				firstSourceEventTick = sourceEventTick;
			}
			else {
				newTick += (sourceEventTick - firstSourceEventTick) * destinationPPQ / sourcePPQ;
			}
			if( ! track.add(new MidiEvent(msg, newTick)) ) continue;
			done = true;
			if( isTimeSignature(msg) ) hasTimeSignature = true;
		}
		if( done ) {
			if( hasTimeSignature ) parent.fireTimeSignatureChanged();
			parent.fireTrackChanged( track );
			int lastIndex = track.size() - 1;
			int oldLastIndex = lastIndex - midiEvents.length;
			fireTableRowsInserted(oldLastIndex, lastIndex);
		}
		return done;
	}
	/**
	 * MIDIイベントを除去します。
	 * @param midiEvents 除去するMIDIイベント
	 */
	public void removeMidiEvents(MidiEvent midiEvents[]) {
		boolean hasTimeSignature = false;
		for( MidiEvent e : midiEvents ) {
			if(isTimeSignature(e.getMessage())) hasTimeSignature = true;
			track.remove(e);
		}
		if(hasTimeSignature) parent.fireTimeSignatureChanged();
		parent.fireTrackChanged(track);
		int lastIndex = track.size() - 1;
		int oldLastIndex = lastIndex + midiEvents.length;
		if(lastIndex < 0) lastIndex = 0;
		fireTableRowsDeleted(oldLastIndex, lastIndex);
	}
	/**
	 * 引数の選択内容が示すMIDIイベントを除去します。
	 * @param selectionModel 選択内容
	 */
	public void removeMidiEvents(ListSelectionModel selectionModel) {
		removeMidiEvents(getMidiEvents(selectionModel));
	}
	private boolean isRhythmPart(int ch) { return (ch == 9); }
	/**
	 * MIDIメッセージの内容を文字列で返します。
	 * @param msg MIDIメッセージ
	 * @return MIDIメッセージの内容を表す文字列
	 */
	public String msgToString(MidiMessage msg) {
		String str = "";
		if( msg instanceof ShortMessage ) {
			ShortMessage shortmsg = (ShortMessage)msg;
			int status = msg.getStatus();
			String status_name = MIDISpec.getStatusName(status);
			int data1 = shortmsg.getData1();
			int data2 = shortmsg.getData2();
			if( MIDISpec.isChannelMessage(status) ) {
				int ch = shortmsg.getChannel();
				String ch_prefix = "Ch."+(ch+1) + ": ";
				String status_prefix = (
						status_name == null ? String.format("status=0x%02X",status) : status_name
						) + ": ";
				int cmd = shortmsg.getCommand();
				switch( cmd ) {
				case ShortMessage.NOTE_OFF:
				case ShortMessage.NOTE_ON:
					str += ch_prefix + status_prefix + data1;
					str += ":[";
					if( isRhythmPart(ch) ) {
						str += MIDISpec.getPercussionName(data1);
					}
					else {
						str += Music.NoteSymbol.noteNoToSymbol(data1);
					}
					str +="] Velocity=" + data2;
					break;
				case ShortMessage.POLY_PRESSURE:
					str += ch_prefix + status_prefix + "Note=" + data1 + " Pressure=" + data2;
					break;
				case ShortMessage.PROGRAM_CHANGE:
					str += ch_prefix + status_prefix + data1 + ":[" + MIDISpec.instrument_names[data1] + "]";
					if( data2 != 0 ) str += " data2=" + data2;
					break;
				case ShortMessage.CHANNEL_PRESSURE:
					str += ch_prefix + status_prefix + data1;
					if( data2 != 0 ) str += " data2=" + data2;
					break;
				case ShortMessage.PITCH_BEND:
				{
					int val = ((data1 & 0x7F) | ((data2 & 0x7F) << 7));
					str += ch_prefix + status_prefix + ( (val-8192) * 100 / 8191) + "% (" + val + ")";
				}
				break;
				case ShortMessage.CONTROL_CHANGE:
				{
					// Control / Mode message name
					String ctrl_name = MIDISpec.getControllerName(data1);
					str += ch_prefix + (data1 < 0x78 ? "CtrlChg: " : "ModeMsg: ");
					if( ctrl_name == null ) {
						str += " No.=" + data1 + " Value=" + data2;
						return str;
					}
					str += ctrl_name;
					//
					// Controller's value
					switch( data1 ) {
					case 0x40: case 0x41: case 0x42: case 0x43: case 0x45:
						str += " " + ( data2==0x3F?"OFF":data2==0x40?"ON":data2 );
						break;
					case 0x44: // Legato Footswitch
						str += " " + ( data2==0x3F?"Normal":data2==0x40?"Legato":data2 );
						break;
					case 0x7A: // Local Control
						str += " " + ( data2==0x00?"OFF":data2==0x7F?"ON":data2 );
						break;
					default:
						str += " " + data2;
						break;
					}
				}
				break;

				default:
					// Never reached here
					break;
				}
			}
			else { // System Message
				str += (status_name == null ? ("status="+status) : status_name );
				str += " (" + data1 + "," + data2 + ")";
			}
			return str;
		}
		else if( msg instanceof MetaMessage ) {
			MetaMessage metamsg = (MetaMessage)msg;
			byte[] msgdata = metamsg.getData();
			int msgtype = metamsg.getType();
			str += "Meta: ";
			String meta_name = MIDISpec.getMetaName(msgtype);
			if( meta_name == null ) {
				str += "Unknown MessageType="+msgtype + " Values=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				return str;
			}
			// Add the message type name
			str += meta_name;
			//
			// Add the text data
			if( MIDISpec.hasMetaText(msgtype) ) {
				str +=" ["+(new String(msgdata))+"]";
				return str;
			}
			// Add the numeric data
			switch(msgtype) {
			case 0x00: // Sequence Number (for MIDI Format 2）
				if( msgdata.length == 2 ) {
					str += String.format(
						": %04X",
						((msgdata[0] & 0xFF) << 8) | (msgdata[1] & 0xFF)
					);
					break;
				}
				str += ": Size not 2 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x20: // MIDI Ch.Prefix
			case 0x21: // MIDI Output Port
				if( msgdata.length == 1 ) {
					str += String.format( ": %02X", msgdata[0] & 0xFF );
					break;
				}
				str += ": Size not 1 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x51: // Tempo
				str += ": " + MIDISpec.byteArrayToQpmTempo( msgdata ) + "[QPM] (";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x54: // SMPTE Offset
				if( msgdata.length == 5 ) {
					str += ": "
						+ (msgdata[0] & 0xFF) + ":"
						+ (msgdata[1] & 0xFF) + ":"
						+ (msgdata[2] & 0xFF) + "."
						+ (msgdata[3] & 0xFF) + "."
						+ (msgdata[4] & 0xFF);
					break;
				}
				str += ": Size not 5 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x58: // Time Signature
				if( msgdata.length == 4 ) {
					str +=": " + msgdata[0] + "/" + (1 << msgdata[1]);
					str +=", "+msgdata[2]+"[clk/beat], "+msgdata[3]+"[32nds/24clk]";
					break;
				}
				str += ": Size not 4 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x59: // Key Signature
				if( msgdata.length == 2 ) {
					Music.Key key = new Music.Key(msgdata);
					str += ": " + key.signatureDescription();
					str += " (" + key.toStringIn(Music.SymbolLanguage.NAME) + ")";
					break;
				}
				str += ": Size not 2 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x7F: // Sequencer Specific Meta Event
				str += " (";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			}
			return str;
		}
		else if( msg instanceof SysexMessage ) {
			SysexMessage sysexmsg = (SysexMessage)msg;
			int status = sysexmsg.getStatus();
			byte[] msgdata = sysexmsg.getData();
			int data_byte_pos = 1;
			switch( status ) {
			case SysexMessage.SYSTEM_EXCLUSIVE:
				str += "SysEx: ";
				break;
			case SysexMessage.SPECIAL_SYSTEM_EXCLUSIVE:
				str += "SysEx(Special): ";
				break;
			default:
				str += "SysEx: Invalid (status="+status+") ";
				break;
			}
			if( msgdata.length < 1 ) {
				str += " Invalid data size: " + msgdata.length;
				return str;
			}
			int manufacturer_id = (int)(msgdata[0] & 0xFF );
			int device_id = (int)(msgdata[1] & 0xFF);
			int model_id = (int)(msgdata[2] & 0xFF);
			String manufacturer_name = MIDISpec.getSysExManufacturerName(manufacturer_id);
			if( manufacturer_name == null ) {
				manufacturer_name = String.format( "[Manufacturer code %02X]", msgdata[0] );
			}
			str += manufacturer_name + String.format( " (DevID=0x%02X)", device_id );
			switch( manufacturer_id ) {
			case 0x7E: // Non-Realtime Universal
				data_byte_pos++;
				int sub_id_1 = (int)(msgdata[2] & 0xFF);
				int sub_id_2 = (int)(msgdata[3] & 0xFF);
				switch( sub_id_1 ) {
				case 0x09: // General MIDI (GM)
					switch( sub_id_2 ) {
					case 0x01: str += " GM System ON"; return str;
					case 0x02: str += " GM System OFF"; return str;
					}
					break;
				default:
					break;
				}
				break;
				// case 0x7F: // Realtime Universal
			case 0x41: // Roland
				data_byte_pos++;
				switch( model_id ) {
				case 0x42:
					str += " [GS]"; data_byte_pos++;
					if( msgdata[3]==0x12 ) {
						str += "DT1:"; data_byte_pos++;
						switch( msgdata[4] ) {
						case 0x00:
							if( msgdata[5]==0x00 ) {
								if( msgdata[6]==0x7F ) {
									if( msgdata[7]==0x00 ) {
										str += " [88] System Mode Set (Mode 1: Single Module)"; return str;
									}
									else if( msgdata[7]==0x01 ) {
										str += " [88] System Mode Set (Mode 2: Double Module)"; return str;
									}
								}
							}
							else if( msgdata[5]==0x01 ) {
								int port = (msgdata[7] & 0xFF);
								str += String.format(
										" [88] Ch.Msg Rx Port: Block=0x%02X, Port=%s",
										msgdata[6],
										port==0?"A":port==1?"B":String.format("0x%02X",port)
										);
								return str;
							}
							break;
						case 0x40:
							if( msgdata[5]==0x00 ) {
								switch( msgdata[6] ) {
								case 0x00: str += " Master Tune: "; data_byte_pos += 3; break;
								case 0x04: str += " Master Volume: "; data_byte_pos += 3; break;
								case 0x05: str += " Master Key Shift: "; data_byte_pos += 3; break;
								case 0x06: str += " Master Pan: "; data_byte_pos += 3; break;
								case 0x7F:
									switch( msgdata[7] ) {
									case 0x00: str += " GS Reset"; return str;
									case 0x7F: str += " Exit GS Mode"; return str;
									}
									break;
								}
							}
							else if( msgdata[5]==0x01 ) {
								switch( msgdata[6] ) {
								// case 0x00: str += ""; break;
								// case 0x10: str += ""; break;
								case 0x30: str += " Reverb Macro: "; data_byte_pos += 3; break;
								case 0x31: str += " Reverb Character: "; data_byte_pos += 3; break;
								case 0x32: str += " Reverb Pre-LPF: "; data_byte_pos += 3; break;
								case 0x33: str += " Reverb Level: "; data_byte_pos += 3; break;
								case 0x34: str += " Reverb Time: "; data_byte_pos += 3; break;
								case 0x35: str += " Reverb Delay FB: "; data_byte_pos += 3; break;
								case 0x36: str += " Reverb Chorus Level: "; data_byte_pos += 3; break;
								case 0x37: str += " [88] Reverb Predelay Time: "; data_byte_pos += 3; break;
								case 0x38: str += " Chorus Macro: "; data_byte_pos += 3; break;
								case 0x39: str += " Chorus Pre-LPF: "; data_byte_pos += 3; break;
								case 0x3A: str += " Chorus Level: "; data_byte_pos += 3; break;
								case 0x3B: str += " Chorus FB: "; data_byte_pos += 3; break;
								case 0x3C: str += " Chorus Delay: "; data_byte_pos += 3; break;
								case 0x3D: str += " Chorus Rate: "; data_byte_pos += 3; break;
								case 0x3E: str += " Chorus Depth: "; data_byte_pos += 3; break;
								case 0x3F: str += " Chorus Send Level To Reverb: "; data_byte_pos += 3; break;
								case 0x40: str += " [88] Chorus Send Level To Delay: "; data_byte_pos += 3; break;
								case 0x50: str += " [88] Delay Macro: "; data_byte_pos += 3; break;
								case 0x51: str += " [88] Delay Pre-LPF: "; data_byte_pos += 3; break;
								case 0x52: str += " [88] Delay Time Center: "; data_byte_pos += 3; break;
								case 0x53: str += " [88] Delay Time Ratio Left: "; data_byte_pos += 3; break;
								case 0x54: str += " [88] Delay Time Ratio Right: "; data_byte_pos += 3; break;
								case 0x55: str += " [88] Delay Level Center: "; data_byte_pos += 3; break;
								case 0x56: str += " [88] Delay Level Left: "; data_byte_pos += 3; break;
								case 0x57: str += " [88] Delay Level Right: "; data_byte_pos += 3; break;
								case 0x58: str += " [88] Delay Level: "; data_byte_pos += 3; break;
								case 0x59: str += " [88] Delay FB: "; data_byte_pos += 3; break;
								case 0x5A: str += " [88] Delay Send Level To Reverb: "; data_byte_pos += 3; break;
								}
							}
							else if( msgdata[5]==0x02 ) {
								switch( msgdata[6] ) {
								case 0x00: str += " [88] EQ Low Freq: "; data_byte_pos += 3; break;
								case 0x01: str += " [88] EQ Low Gain: "; data_byte_pos += 3; break;
								case 0x02: str += " [88] EQ High Freq: "; data_byte_pos += 3; break;
								case 0x03: str += " [88] EQ High Gain: "; data_byte_pos += 3; break;
								}
							}
							else if( msgdata[5]==0x03 ) {
								if( msgdata[6] == 0x00 ) {
									str += " [Pro] EFX Type: "; data_byte_pos += 3;
								}
								else if( msgdata[6] >= 0x03 && msgdata[6] <= 0x16 ) {
									str += String.format(" [Pro] EFX Param %d", msgdata[6]-2 );
									data_byte_pos += 3;
								}
								else if( msgdata[6] == 0x17 ) {
									str += " [Pro] EFX Send Level To Reverb: "; data_byte_pos += 3;
								}
								else if( msgdata[6] == 0x18 ) {
									str += " [Pro] EFX Send Level To Chorus: "; data_byte_pos += 3;
								}
								else if( msgdata[6] == 0x19 ) {
									str += " [Pro] EFX Send Level To Delay: "; data_byte_pos += 3;
								}
								else if( msgdata[6] == 0x1B ) {
									str += " [Pro] EFX Ctrl Src1: "; data_byte_pos += 3;
								}
								else if( msgdata[6] == 0x1C ) {
									str += " [Pro] EFX Ctrl Depth1: "; data_byte_pos += 3;
								}
								else if( msgdata[6] == 0x1D ) {
									str += " [Pro] EFX Ctrl Src2: "; data_byte_pos += 3;
								}
								else if( msgdata[6] == 0x1E ) {
									str += " [Pro] EFX Ctrl Depth2: "; data_byte_pos += 3;
								}
								else if( msgdata[6] == 0x1F ) {
									str += " [Pro] EFX Send EQ Switch: "; data_byte_pos += 3;
								}
							}
							else if( (msgdata[5] & 0xF0) == 0x10 ) {
								int ch = (msgdata[5] & 0x0F);
								if( ch <= 9 ) ch--; else if( ch == 0 ) ch = 9;
								if( msgdata[6]==0x02 ) {
									str += String.format(
											" Rx Ch: Part=%d(0x%02X) Ch=0x%02X", (ch+1),  msgdata[5], msgdata[7]
											);
									return str;
								}
								else if( msgdata[6]==0x15 ) {
									String map;
									switch( msgdata[7] ) {
									case 0: map = " NormalPart"; break;
									case 1: map = " DrumMap1"; break;
									case 2: map = " DrumMap2"; break;
									default: map = String.format("0x%02X",msgdata[7]); break;
									}
									str += String.format(
											" Rhythm Part: Ch=%d(0x%02X) Map=%s",
											(ch+1), msgdata[5],
											map
											);
									return str;
								}
							}
							else if( (msgdata[5] & 0xF0) == 0x40 ) {
								int ch = (msgdata[5] & 0x0F);
								if( ch <= 9 ) ch--; else if( ch == 0 ) ch = 9;
								int dt = (msgdata[7] & 0xFF);
								if( msgdata[6]==0x20 ) {
									str += String.format(
											" [88] EQ: Ch=%d(0x%02X) %s",
											(ch+1), msgdata[5],
											dt==0 ? "OFF" : dt==1 ? "ON" : String.format("0x%02X",dt)
											);
								}
								else if( msgdata[6]==0x22 ) {
									str += String.format(
											" [Pro] Part EFX Assign: Ch=%d(0x%02X) %s",
											(ch+1), msgdata[5],
											dt==0 ? "ByPass" : dt==1 ? "EFX" : String.format("0x%02X",dt)
											);
								}
							}
							break;
						} // [4]
					} // [3] [DT1]
					break; // [GS]
				case 0x45:
					str += " [GS-LCD]"; data_byte_pos++;
					if( msgdata[3]==0x12 ) {
						str += " [DT1]"; data_byte_pos++;
						if( msgdata[4]==0x10 && msgdata[5]==0x00 && msgdata[6]==0x00 ) {
							data_byte_pos += 3;
							str += " Disp [" +(new String(
									msgdata, data_byte_pos, msgdata.length - data_byte_pos - 2
									))+ "]";
						}
					} // [3] [DT1]
					break;
				case 0x14: str += " [D-50]"; data_byte_pos++; break;
				case 0x16: str += " [MT-32]"; data_byte_pos++; break;
				} // [2] model_id
				break;
			case 0x43: // Yamaha (XG)
				data_byte_pos++;
				if( model_id == 0x4C ) {
					str += " [XG]";
					if( msgdata[3]==0 && msgdata[4]==0 && msgdata[5]==0x7E && msgdata[6]==0 ) {
						str += " XG System ON"; return str;
					}
					data_byte_pos++;
				}
				break;
			default:
				break;
			}
			int i;
			str += " data=(";
			for( i = data_byte_pos; i<msgdata.length-1; i++ ) {
				str += String.format( " %02X", msgdata[i] );
			}
			if( i < msgdata.length && (int)(msgdata[i] & 0xFF) != 0xF7 ) {
				str+=" [ Invalid EOX " + String.format( "%02X", msgdata[i] ) + " ]";
			}
			str += " )";
			return str;
		}
		byte[] msg_data = msg.getMessage();
		str += "(";
		for( byte b : msg_data ) {
			str += String.format( " %02X", b );
		}
		str += " )";
		return str;
	}
}

/**
 *  MIDI シーケンスデータのtickインデックス
 * <p>拍子、テンポ、調だけを抜き出したトラックを保持するためのインデックスです。
 * 指定の MIDI tick の位置におけるテンポ、調、拍子を取得したり、
 * 拍子情報から MIDI tick と小節位置との間の変換を行うために使います。
 * </p>
 */
class SequenceTickIndex {
	/**
	 * メタメッセージの種類：テンポ
	 */
	public static final int TEMPO = 0;
	/**
	 * メタメッセージの種類：拍子
	 */
	public static final int TIME_SIGNATURE = 1;
	/**
	 * メタメッセージの種類：調号
	 */
	public static final int KEY_SIGNATURE = 2;
	/**
	 * メタメッセージタイプ → メタメッセージの種類 変換マップ
	 */
	private static final Map<Integer,Integer> INDEX_META_TO_TRACK =
		new HashMap<Integer,Integer>() {
			{
				put(0x51, TEMPO);
				put(0x58, TIME_SIGNATURE);
				put(0x59, KEY_SIGNATURE);
			}
		};
	/**
	 * 新しいMIDIシーケンスデータのインデックスを構築します。
	 * @param sourceSequence 元のMIDIシーケンス
	 */
	public SequenceTickIndex(Sequence sourceSequence) {
		try {
			int ppq = sourceSequence.getResolution();
			wholeNoteTickLength = ppq * 4;
			tmpSequence = new Sequence(Sequence.PPQ, ppq, 3);
			tracks = tmpSequence.getTracks();
			Track[] sourceTracks = sourceSequence.getTracks();
			for( Track tk : sourceTracks ) {
				for( int i_evt = 0 ; i_evt < tk.size(); i_evt++ ) {
					MidiEvent evt = tk.get(i_evt);
					MidiMessage msg = evt.getMessage();
					if( ! (msg instanceof MetaMessage) )
						continue;
					MetaMessage metaMsg = (MetaMessage)msg;
					int metaType = metaMsg.getType();
					Integer metaIndex = INDEX_META_TO_TRACK.get(metaType);
					if( metaIndex != null ) tracks[metaIndex].add(evt);
				}
			}
		}
		catch ( InvalidMidiDataException e ) {
			e.printStackTrace();
		}
	}
	private Sequence tmpSequence;
	/**
	 * このtickインデックスのタイミング解像度を返します。
	 * @return このtickインデックスのタイミング解像度
	 */
	public int getResolution() {
		return tmpSequence.getResolution();
	}
	private Track[] tracks;
	/**
	 * 指定されたtick位置以前の最後のメタメッセージを返します。
	 * @param trackIndex メタメッセージの種類（）
	 * @param tickPosition
	 * @return
	 */
	public MetaMessage lastMetaMessageAt(int trackIndex, long tickPosition) {
		Track track = tracks[trackIndex];
		for(int eventIndex = track.size()-1 ; eventIndex >= 0; eventIndex--) {
			MidiEvent event = track.get(eventIndex);
			if( event.getTick() > tickPosition )
				continue;
			MetaMessage metaMessage = (MetaMessage)(event.getMessage());
			if( metaMessage.getType() == 0x2F /* skip EOT (last event) */ )
				continue;
			return metaMessage;
		}
		return null;
	}

	private int wholeNoteTickLength;
	public int lastBeat;
	public int lastExtraTick;
	public byte timesigUpper;
	public byte timesigLowerIndex;
	/**
	 * tick位置を小節位置に変換します。
	 * @param tickPosition tick位置
	 * @return 小節位置
	 */
	int tickToMeasure(long tickPosition) {
		byte extraBeats = 0;
		MidiEvent event = null;
		MidiMessage message = null;
		byte[] data = null;
		long currentTick = 0L;
		long nextTimesigTick = 0L;
		long prevTick = 0L;
		long duration = 0L;
		int lastMeasure = 0;
		int eventIndex = 0;
		timesigUpper = 4;
		timesigLowerIndex = 2; // =log2(4)
		if( tracks[TIME_SIGNATURE] != null ) {
			do {
				// Check current time-signature event
				if( eventIndex < tracks[TIME_SIGNATURE].size() ) {
					message = (event = tracks[TIME_SIGNATURE].get(eventIndex)).getMessage();
					currentTick = nextTimesigTick = event.getTick();
					if(currentTick > tickPosition || (message.getStatus() == 0xFF && ((MetaMessage)message).getType() == 0x2F /* EOT */)) {
						currentTick = tickPosition;
					}
				}
				else { // No event
					currentTick = nextTimesigTick = tickPosition;
				}
				// Add measure from last event
				//
				int beatTickLength = wholeNoteTickLength >> timesigLowerIndex;
				duration = currentTick - prevTick;
				int beats = (int)( duration / beatTickLength );
				lastExtraTick = (int)(duration % beatTickLength);
				int measures = beats / timesigUpper;
				extraBeats = (byte)(beats % timesigUpper);
				lastMeasure += measures;
				if( nextTimesigTick > tickPosition ) break;  // Not reached to next time signature
				//
				// Reached to the next time signature, so get it.
				if( ( data = ((MetaMessage)message).getData() ).length > 0 ) { // To skip EOT, check the data length.
					timesigUpper = data[0];
					timesigLowerIndex = data[1];
				}
				if( currentTick == tickPosition )  break;  // Calculation complete
				//
				// Calculation incomplete, so prepare for next
				//
				if( extraBeats > 0 ) {
					//
					// Extra beats are treated as 1 measure
					lastMeasure++;
				}
				prevTick = currentTick;
				eventIndex++;
			} while( true );
		}
		lastBeat = extraBeats;
		return lastMeasure;
	}
	/**
	 * 小節位置を MIDI tick に変換します。
	 * @param measure 小節位置
	 * @return MIDI tick
	 */
	public long measureToTick(int measure) {
		return measureToTick(measure, 0, 0);
	}
	/**
	 * 指定の小節位置、拍、拍内tickを、そのシーケンス全体の MIDI tick に変換します。
	 * @param measure 小節位置
	 * @param beat 拍
	 * @param extraTick 拍内tick
	 * @return そのシーケンス全体の MIDI tick
	 */
	public long measureToTick(int measure, int beat, int extraTick) {
		MidiEvent evt = null;
		MidiMessage msg = null;
		byte[] data = null;
		long tick = 0L;
		long prev_tick = 0L;
		long duration = 0L;
		long duration_sum = 0L;
		long estimated_ticks;
		int ticks_per_beat;
		int i_evt = 0;
		timesigUpper = 4;
		timesigLowerIndex = 2; // =log2(4)
		do {
			ticks_per_beat = wholeNoteTickLength >> timesigLowerIndex;
			estimated_ticks = ((measure * timesigUpper) + beat) * ticks_per_beat + extraTick;
			if( tracks[TIME_SIGNATURE] == null || i_evt > tracks[TIME_SIGNATURE].size() ) {
				return duration_sum + estimated_ticks;
			}
			msg = (evt = tracks[TIME_SIGNATURE].get(i_evt)).getMessage();
			if( msg.getStatus() == 0xFF && ((MetaMessage)msg).getType() == 0x2F /* EOT */ ) {
				return duration_sum + estimated_ticks;
			}
			duration = (tick = evt.getTick()) - prev_tick;
			if( duration >= estimated_ticks ) {
				return duration_sum + estimated_ticks;
			}
			// Re-calculate measure (ignore extra beats/ticks)
			measure -= ( duration / (ticks_per_beat * timesigUpper) );
			duration_sum += duration;
			//
			// Get next time-signature
			data = ( (MetaMessage)msg ).getData();
			timesigUpper = data[0];
			timesigLowerIndex = data[1];
			prev_tick = tick;
			i_evt++;
		} while( true );
	}
}
