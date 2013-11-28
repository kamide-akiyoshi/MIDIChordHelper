
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import javax.sound.midi.MetaEventListener;
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
import javax.swing.DefaultListSelectionModel;
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
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

/**
 * MIDIエディタ（MIDI Editor/Playlist for MIDI Chord Helper）
 *
 * @author
 *	Copyright (C) 2006-2013 Akiyoshi Kamide
 *	http://www.yk.rim.or.jp/~kamide/music/chordhelper/
 */
class MidiEditor extends JDialog implements DropTargetListener {
	public static final Insets ZERO_INSETS = new Insets(0,0,0,0);
	static final Icon deleteIcon = new ButtonIcon(ButtonIcon.X_ICON);
	/**
	 * このMIDIエディタの仮想MIDIデバイス
	 */
	VirtualMidiDevice virtualMidiDevice = new AbstractVirtualMidiDevice() {
		class MyInfo extends Info {
			protected MyInfo() {
				super("MIDI Editor","Unknown vendor","MIDI sequence editor","");
			}
		}
		// 送信のみなので MIDI IN はサポートしない
		{ info = new MyInfo(); setMaxReceivers(0); }
	};
	/**
	 * このダイアログを表示するアクション
	 */
	public Action openAction = new AbstractAction(
		"Edit/Playlist/Speed", new ButtonIcon(ButtonIcon.EDIT_ICON)
	) {
		{
			String tooltip = "MIDIシーケンスの編集／プレイリスト／再生速度調整";
			putValue(Action.SHORT_DESCRIPTION, tooltip);
		}
		@Override
		public void actionPerformed(ActionEvent e) { open(); }
	};
	/**
	 * このダイアログを開きます。すでに開かれていた場合は前面に移動します。
	 */
	public void open() {
		if( isVisible() ) toFront(); else setVisible(true);
	}
	/**
	 * エラーメッセージダイアログを表示します。
	 * @param message エラーメッセージ
	 */
	private void showError(String message) {
		JOptionPane.showMessageDialog(
			this, message,
			ChordHelperApplet.VersionInfo.NAME,
			JOptionPane.ERROR_MESSAGE
		);
	}
	/**
	 * 警告メッセージダイアログを表示します。
	 * @param message 警告メッセージ
	 */
	private void showWarning(String message) {
		JOptionPane.showMessageDialog(
			this, message,
			ChordHelperApplet.VersionInfo.NAME,
			JOptionPane.WARNING_MESSAGE
		);
	}
	/**
	 * 確認ダイアログを表示します。
	 * @param message 確認メッセージ
	 * @return 確認OKのときtrue
	 */
	boolean confirm(String message) {
		return JOptionPane.showConfirmDialog(
			this, message,
			ChordHelperApplet.VersionInfo.NAME,
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE
		) == JOptionPane.YES_OPTION ;
	}

	/**
	 * 新しいMIDIシーケンスを生成するダイアログ
	 */
	NewSequenceDialog newSequenceDialog = new NewSequenceDialog(this);
	/**
	 * BASE64テキスト入力ダイアログ
	 */
	Base64Dialog base64Dialog = new Base64Dialog(this);
	/**
	 * プレイリストのデータモデル
	 */
	SequenceListTableModel sequenceListTableModel;
	/**
	 * プレイリストビュー
	 */
	SequenceListTable sequenceListTableView;
	/**
	 * プレイリストビュー
	 */
	private class SequenceListTable extends JTable {
		/**
		 * プレイリストビューを構築します。
		 * @param model プレイリストデータモデル
		 */
		public SequenceListTable(SequenceListTableModel model) {
			super(model, null, model.selectionModel);
			try {
				midiFileChooser = new MidiFileChooser();
			}
			catch( ExceptionInInitializerError|NoClassDefFoundError|AccessControlException e ) {
				// アプレットの場合、Webクライアントマシンのローカルファイルには
				// アクセスできないので、ファイル選択ダイアログは使用不可。
				midiFileChooser = null;
			}
			model.selectionModel.addListSelectionListener(
				new ListSelectionListener() {
					@Override
					public void valueChanged(ListSelectionEvent e) {
						if( e.getValueIsAdjusting() )
							return;
						updateButtonStatus();
					}
				}
			);
			model.addTableModelListener(new TableModelListener() {
				/**
				 * 全シーケンスの合計時間長をヘッダータイトルに反映します。
				 * @param e テーブルモデルイベント
				 */
				@Override
				public void tableChanged(TableModelEvent e) {
					int sec = getModel().getTotalSeconds();
					SequenceListTableModel.Column c = SequenceListTableModel.Column.SEQ_LENGTH;
					TableColumn tc = getColumnModel().getColumn(c.ordinal());
					tc.setHeaderValue(String.format(c.title+" [%02d:%02d]", sec/60, sec%60));
					//
					// シーケンス削除時など、合計シーケンス長が変わっても
					// 列モデルからではヘッダタイトルが再描画されないことがある。
					// そこで、ヘッダビューから repaint() で突っついて再描画させる。
					getTableHeader().repaint();
				}
			});
			SequenceListTableModel.Column c = SequenceListTableModel.Column.SEQ_PLAY;
			TableColumn tc = getColumnModel().getColumn(c.ordinal());
			tc.setCellRenderer(new TableCellRenderer() {
				@Override
				public Component getTableCellRendererComponent(
					JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column
				) {
					if(getModel().sequenceList.get(row).isOnSequencer()) {
						// すでにロードされていたらボタンをレンダリング
						return playButton;
					}
					// ロードされていなかった場合、
					// デフォルトレンダラーでレンダリングする。こうすれば
					// レンダラーを設定しなかった場合と全く同じ動作になる。
					Class<?> columnClass = getModel().getColumnClass(column);
					TableCellRenderer defaultRenderer =
						table.getDefaultRenderer(columnClass);
					return defaultRenderer.getTableCellRendererComponent(
						table, value, isSelected, hasFocus, row, column
					);
				}
			});
			tc.setCellEditor(new PlayButtonCellEditor());
			setAutoCreateColumnsFromModel(false);
			if( base64Dialog.isBase64Available() ) {
				base64EncodeAction = getModel().new SelectedSequenceAction(
					"Base64 Encode",
					"Encode selected sequence to Base64 textdata - 選択した曲をBase64テキストにエンコード"
				) {
					@Override
					public void actionPerformed(ActionEvent e) {
						SequenceTrackListTableModel mstm = getModel().getSelectedSequenceModel();
						base64Dialog.setMIDIData(mstm.getMIDIdata(), mstm.getFilename());
						base64Dialog.setVisible(true);
					}
				};
			}
		}
		@Override
		public SequenceListTableModel getModel() {
			return (SequenceListTableModel)(super.getModel());
		}
		class PlayButtonCellEditor extends AbstractCellEditor implements TableCellEditor {
			@Override
			public Object getCellEditorValue() { return ""; }
			@Override
			public Component getTableCellEditorComponent(
				JTable table, Object value, boolean isSelected,
				int row, int column
			) {
				return getModel().sequenceList.get(row).isOnSequencer() ? playButton : null;
			}
		}
		/**
		 * セル埋め込み用プレイボタン
		 */
		private JToggleButton playButton = new JToggleButton(
			getModel().sequencerModel.startStopAction
		){{ setMargin(ZERO_INSETS); }};
		/**
		 * BASE64エンコードボタン（ライブラリが見えている場合のみ有効）
		 */
		Action base64EncodeAction;
		/**
		 * シーケンスを削除するアクション
		 */
		Action deleteSequenceAction = getModel().new SelectedSequenceAction(
			"Delete", MidiEditor.deleteIcon,
			"Delete selected MIDI sequence - 選択した曲をプレイリストから削除"
		) {
			@Override
			public void actionPerformed(ActionEvent e) {
				SequenceListTableModel model = getModel();
				if( midiFileChooser != null ) {
					// ファイルに保存できる場合（Javaアプレットではなく、Javaアプリとして動作している場合）
					//
					SequenceTrackListTableModel seqModel = model.getSelectedSequenceModel();
					if( seqModel.isModified() ) {
						// ファイル未保存の変更がある場合
						//
						String message =
							"Selected MIDI sequence not saved - delete it ?\n" +
							"選択したMIDIシーケンスはまだ保存されていません。削除しますか？";
						if( ! confirm(message) ) {
							// 実は削除してほしくなかった場合
							return;
						}
					}
				}
				// 削除を実行
				model.removeSelectedSequence();
			}
		};
		/**
		 * ファイル選択ダイアログ（アプレットでは使用不可）
		 */
		private MidiFileChooser midiFileChooser;
		/**
		 * ファイル選択ダイアログ（アプレットでは使用不可）
		 */
		private class MidiFileChooser extends JFileChooser implements ListSelectionListener {
			/**
			 * ファイル保存アクション
			 */
			public Action saveMidiFileAction = new AbstractAction("Save") {
				{
					String tooltip = "Save selected MIDI sequence to file - 選択したMIDIシーケンスをファイルに保存";
					putValue(Action.SHORT_DESCRIPTION, tooltip);
				}
				@Override
				public void actionPerformed(ActionEvent e) {
					SequenceTrackListTableModel sequenceTableModel =
						getModel().getSelectedSequenceModel();
					String filename = sequenceTableModel.getFilename();
					File midiFile;
					if( filename != null && ! filename.isEmpty() ) {
						// プレイリスト上でファイル名が入っていたら、それを初期選択
						setSelectedFile(midiFile = new File(filename));
					}
					int response = showSaveDialog(MidiEditor.this);
					if( response != JFileChooser.APPROVE_OPTION ) {
						// 保存ダイアログでキャンセルされた場合
						return;
					}
					if( (midiFile = getSelectedFile()).exists() ) {
						// 指定されたファイルがすでにあった場合
						String fn = midiFile.getName();
						String message = "Overwrite " + fn + " ?\n";
						message += fn + " を上書きしてよろしいですか？";
						if( ! confirm(message) ) {
							// 上書きしてほしくなかった場合
							return;
						}
					}
					// 保存を実行
					try ( FileOutputStream out = new FileOutputStream(midiFile) ) {
						out.write(sequenceTableModel.getMIDIdata());
						sequenceTableModel.setModified(false);
					}
					catch( IOException ex ) {
						showError( ex.getMessage() );
						ex.printStackTrace();
					}
				}
			};
			{
				// ファイルフィルタの設定
				setFileFilter(new FileNameExtensionFilter("MIDI sequence (*.mid)", "mid"));
				//
				// 選択状態のリスニングを開始
				getModel().selectionModel.addListSelectionListener(this);
				updateEnabled();
			}
			/**
			 * シーケンスの選択有無に応じて、保存ボタンのイネーブル状態を更新します。
			 */
			private void updateEnabled() {
				boolean en = (getModel().selectionModel.getMinSelectionIndex() >= 0);
				saveMidiFileAction.setEnabled(en);
			}
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if( e.getValueIsAdjusting() )
					return;
				updateEnabled();
			}
			/**
			 * ファイルを開くアクション
			 */
			public Action openMidiFileAction = new AbstractAction("Open") {
				{
					String tooltip = "Open MIDI file - MIDIファイルを開く";
					putValue(Action.SHORT_DESCRIPTION, tooltip);
				}
				@Override
				public void actionPerformed(ActionEvent event) {
					if(showOpenDialog(MidiEditor.this) == JFileChooser.APPROVE_OPTION) {
						try  {
							getModel().addSequence(getSelectedFile());
						} catch( IOException|InvalidMidiDataException e ) {
							showWarning(e.getMessage());
						} catch( AccessControlException e ) {
							showError(e.getMessage());
							e.printStackTrace();
						}
					}
				}
			};
		};
	}
	//
	// Drag & drop
	public void dragEnter(DropTargetDragEvent event) {
		if( event.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ) {
			event.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
		}
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
				loadAndPlay((List<File>)data);
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

	SequenceTrackListTableModel sequenceTrackListTableModel;
	/**
	 * MIDIトラック選択状態
	 */
	ListSelectionModel trackSelectionModel;
	/**
	 * トラック追加アクション
	 */
	public Action addTrackAction = new AbstractAction("New") {
		{
			String tooltip = "Append new track - 新しいトラックの追加";
			putValue(Action.SHORT_DESCRIPTION, tooltip);
		}
		@Override
		public void actionPerformed(ActionEvent e) {
			int index = sequenceListTableModel.getSelectedSequenceModel().createTrack();
			trackSelectionModel.setSelectionInterval(index, index);
		}
	};
	/**
	 * トラック削除アクション
	 */
	public Action deleteTrackAction = new AbstractAction("Delete", deleteIcon) {
		{
			String tooltip = "Delete selected track - 選択したトラックを削除";
			putValue(Action.SHORT_DESCRIPTION, tooltip);
		}
		@Override
		public void actionPerformed(ActionEvent e) {
			if( ! confirm("Do you want to delete selected track ?\n選択したトラックを削除しますか？"))
				return;
			sequenceListTableModel.getSelectedSequenceModel().deleteTracks(
				trackSelectionModel
			);
		}
	};
	/**
	 * MIDIトラックリストテーブルビュー（選択中のシーケンスの中身）
	 */
	private JTable trackListTableView;

	/**
	 * MIDIイベント選択状態
	 */
	private ListSelectionModel eventSelectionModel = new DefaultListSelectionModel() {{
		setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if( e.getValueIsAdjusting() ) return;
				if( ! isSelectionEmpty() ) {
					TrackEventListTableModel trackModel = (TrackEventListTableModel)trackEventListTableView.getModel();
					int minIndex = getMinSelectionIndex();
					if( trackModel.hasTrack() ) {
						MidiEvent midiEvent = trackModel.getMidiEvent(minIndex);
						MidiMessage msg = midiEvent.getMessage();
						if( msg instanceof ShortMessage ) {
							ShortMessage sm = (ShortMessage)msg;
							int cmd = sm.getCommand();
							if( cmd == 0x80 || cmd == 0x90 || cmd == 0xA0 ) {
								// ノート番号を持つ場合、音を鳴らす。
								MidiChannel outMidiChannels[] = virtualMidiDevice.getChannels();
								int ch = sm.getChannel();
								int note = sm.getData1();
								int vel = sm.getData2();
								outMidiChannels[ch].noteOn(note, vel);
								outMidiChannels[ch].noteOff(note, vel);
							}
						}
					}
					if( pairNoteCheckbox.isSelected() ) {
						int maxIndex = getMaxSelectionIndex();
						int partnerIndex;
						for( int i=minIndex; i<=maxIndex; i++ )
							if(
								isSelectedIndex(i) &&
								(partnerIndex = trackModel.getIndexOfPartnerFor(i)) >= 0 &&
								! isSelectedIndex(partnerIndex)
							) addSelectionInterval(partnerIndex, partnerIndex);
					}
				}
				updateButtonStatus();
			}
		});
	}};
	/**
	 * MIDIイベントリストテーブルビュー
	 */
	private JTable trackEventListTableView;
	private class MidiEventsLabel extends JLabel implements ListSelectionListener {
		private static final String TITLE = "MIDI Events";
		public MidiEventsLabel() {
			super(TITLE);
			trackSelectionModel.addListSelectionListener(this);
		}
		@Override
		public void valueChanged(ListSelectionEvent e) {
			String text = TITLE;
			int index = trackSelectionModel.getMinSelectionIndex();
			if( index >= 0 )
				text = String.format(TITLE+" - track No.%d", index);
			setText(text);
		}
	}
	/**
	 * スクロール可能なMIDIイベントテーブルビュー
	 */
	private JScrollPane scrollableEventTableView;
	/**
	 * 指定の MIDI tick のイベントへスクロールします。
	 * @param tick MIDI tick
	 */
	public void scrollToEventAt(long tick) {
		TrackEventListTableModel trackModel = (TrackEventListTableModel)trackEventListTableView.getModel();
		int index = trackModel.tickToIndex(tick);
		scrollableEventTableView.getVerticalScrollBar().setValue(
			index * trackEventListTableView.getRowHeight()
		);
		eventSelectionModel.setSelectionInterval(index, index);
	}
	/**
	 * MIDIイベント表のセルエディタ
	 */
	MidiEventCellEditor eventCellEditor = new MidiEventCellEditor();
	/**
	 * Pair note on/off チェックボックス
	 */
	private JCheckBox pairNoteCheckbox = new JCheckBox("Pair NoteON/OFF") {
		{ setModel(eventCellEditor.pairNoteOnOffModel); }
	};
	/**
	 * MIDIイベント表のセルエディタ
	 */
	class MidiEventCellEditor extends AbstractCellEditor implements TableCellEditor {
		/**
		 * MIDIイベント入力ダイアログ
		 */
		MidiEventDialog	eventDialog = new MidiEventDialog();
		/**
		 * 削除対象にする変更前イベント（null可）
		 */
		private MidiEvent[] midiEventsToBeRemoved;
		/**
		 * 対象トラック
		 */
		private TrackEventListTableModel midiTrackTableModel;
		/**
		 * 対象シーケンス
		 */
		private SequenceTrackListTableModel sequenceTableModel;
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
			new JToggleButton.ToggleButtonModel() {{
				addItemListener(new ItemListener() {
					public void itemStateChanged(ItemEvent e) {
						eventDialog.midiMessageForm.durationForm.setEnabled(isSelected());
					}
				});
				setSelected(true);
			}};

		private void setSelectedEvent() {
			sequenceTableModel = sequenceListTableModel.getSelectedSequenceModel();
			eventDialog.midiMessageForm.durationForm.setPPQ(sequenceTableModel.getSequence().getResolution());
			tickPositionModel.setSequenceIndex(sequenceTableModel.getSequenceTickIndex());
			selectedIndex = -1;
			currentTick = 0;
			selectedMidiEvent = null;
			midiTrackTableModel = (TrackEventListTableModel)trackEventListTableView.getModel();
			if( ! eventSelectionModel.isSelectionEmpty() ) {
				selectedIndex = eventSelectionModel.getMinSelectionIndex();
				selectedMidiEvent = midiTrackTableModel.getMidiEvent(selectedIndex);
				currentTick = selectedMidiEvent.getTick();
				tickPositionModel.setTickPosition(currentTick);
			}
		}
		/**
		 * イベント入力をキャンセルするアクション
		 */
		Action cancelAction = new AbstractAction() {
			{
				putValue(NAME,"Cancel");
				eventDialog.cancelButton.setAction(this);
			}
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
					((TrackEventListTableModel)trackEventListTableView.getModel()).addMidiEvents(
						copiedEventsToPaste, tick, copiedEventsPPQ
					);
					scrollToEventAt(tick);
					// プレイリストの曲の長さ表示を更新
					sequenceListTableModel.fireSelectedSequenceChanged();
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
				sequenceListTableModel.fireSequenceModified(sequenceTableModel);
				eventDialog.setVisible(false);
				fireEditingStopped();
			}
		};
		/**
		 * イベント編集アクション
		 */
		private Action editEventAction = new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				setSelectedEvent();
				if( selectedMidiEvent == null )
					return;
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
		};
		public MidiEventCellEditor() {
			eventDialog.midiMessageForm.setOutputMidiChannels(virtualMidiDevice.getChannels());
			eventDialog.tickPositionInputForm.setModel(tickPositionModel);
		}
		@Override
		public boolean isCellEditable(EventObject e) {
			// ダブルクリックで編集
			return e instanceof MouseEvent && ((MouseEvent)e).getClickCount() == 2;
		}
		@Override
		public Object getCellEditorValue() { return ""; }
		private JButton editEventButton = new JButton(editEventAction){
			{
				setHorizontalAlignment(JButton.LEFT);
			}
		};
		@Override
		public Component getTableCellEditorComponent(
			JTable table, Object value, boolean isSelected, int row, int column
		) {
			editEventButton.setText((String)value);
			return editEventButton;
		}
	}

	/**
	 * ペースト用にコピーされたMIDIイベントの配列
	 */
	private MidiEvent copiedEventsToPaste[];
	/**
	 * ペースト用にコピーされたMIDIイベントのタイミング解像度
	 */
	private int copiedEventsPPQ = 0;
	/**
	 * イベントカットアクション
	 */
	public Action cutEventAction = new AbstractAction("Cut") {
		@Override
		public void actionPerformed(ActionEvent e) {
			if( ! confirm("Do you want to cut selected event ?\n選択したMIDIイベントを切り取りますか？"))
				return;
			TrackEventListTableModel trackTableModel = (TrackEventListTableModel)trackEventListTableView.getModel();
			copiedEventsToPaste = trackTableModel.getMidiEvents(eventSelectionModel);
			copiedEventsPPQ = sequenceListTableModel.getSelectedSequenceModel().getSequence().getResolution();
			trackTableModel.removeMidiEvents(copiedEventsToPaste);
			// プレイリストの曲の長さ表示を更新
			sequenceListTableModel.fireSelectedSequenceChanged();
		}
	};
	/**
	 * イベントコピーアクション
	 */
	public Action copyEventAction = new AbstractAction("Copy") {
		@Override
		public void actionPerformed(ActionEvent e) {
			TrackEventListTableModel trackTableModel = (TrackEventListTableModel)trackEventListTableView.getModel();
			copiedEventsToPaste = trackTableModel.getMidiEvents(eventSelectionModel);
			copiedEventsPPQ = sequenceListTableModel.getSelectedSequenceModel().getSequence().getResolution();
			updateButtonStatus();
		}
	};
	/**
	 * イベント削除アクション
	 */
	public Action deleteEventAction = new AbstractAction("Delete", deleteIcon) {
		@Override
		public void actionPerformed(ActionEvent e) {
			if( ! confirm("Do you want to delete selected event ?\n選択したMIDIイベントを削除しますか？"))
				return;
			((TrackEventListTableModel)trackEventListTableView.getModel()).removeMidiEvents(eventSelectionModel);
			// プレイリストの曲の長さ表示を更新
			sequenceListTableModel.fireSelectedSequenceChanged();
		}
	};

	/**
	 * 新しい {@link MidiEditor} を構築します。
	 * @param deviceModelList MIDIデバイスモデルリスト
	 */
	public MidiEditor(MidiSequencerModel sequencerModel) {
		sequenceListTableModel = new SequenceListTableModel(sequencerModel) ;
		sequenceTrackListTableModel = new SequenceTrackListTableModel(sequenceListTableModel);
		setTitle("MIDI Editor/Playlist - MIDI Chord Helper");
		setBounds( 150, 200, 850, 500 );
		setLayout(new FlowLayout());
		new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, this, true);
		JPanel playlistPanel = new JPanel() {{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			add(new JScrollPane(
				sequenceListTableView = new SequenceListTable(sequenceListTableModel)
			));
			add(Box.createRigidArea(new Dimension(0, 10)));
			add(new JPanel() {{
				setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
				add(Box.createRigidArea(new Dimension(10, 0)));
				add(new JButton(newSequenceDialog.openAction) {{
					setMargin(ZERO_INSETS);
				}});
				if( sequenceListTableView.midiFileChooser != null ) {
					add( Box.createRigidArea(new Dimension(5, 0)) );
					add(new JButton(
						sequenceListTableView.midiFileChooser.openMidiFileAction
					) {{
						setMargin(ZERO_INSETS);
					}});
				}
				add(Box.createRigidArea(new Dimension(5, 0)));
				add(new JButton(sequenceListTableModel.moveToTopAction) {{
					setMargin(ZERO_INSETS);
				}});
				add(Box.createRigidArea(new Dimension(5, 0)));
				add(new JButton(sequenceListTableModel.loadToSequencerAction){{
					setMargin(ZERO_INSETS);
				}});
				add(Box.createRigidArea(new Dimension(5, 0)));
				add(new JButton(sequenceListTableModel.moveToBottomAction) {{
					setMargin(ZERO_INSETS);
				}});
				if( sequenceListTableView.midiFileChooser != null ) {
					add(Box.createRigidArea(new Dimension(5, 0)));
					add(new JButton(
						sequenceListTableView.midiFileChooser.saveMidiFileAction
					) {{
						setMargin(ZERO_INSETS);
					}});
				}
				if(sequenceListTableView.base64EncodeAction != null) {
					add(Box.createRigidArea(new Dimension(5, 0)));
					add(new JButton(sequenceListTableView.base64EncodeAction) {{
						setMargin(ZERO_INSETS);
					}});
				}
				add( Box.createRigidArea(new Dimension(5, 0)) );
				add(new JButton(sequenceListTableView.deleteSequenceAction) {{
					setMargin(ZERO_INSETS);
				}});
				add( Box.createRigidArea(new Dimension(5, 0)) );
				add(new SequencerSpeedSlider(
					sequenceListTableModel.sequencerModel.speedSliderModel
				));
			}});
			add( Box.createRigidArea(new Dimension(0, 10)) );
		}};
		JPanel trackListPanel = new JPanel() {{
			setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
			add(new JLabel() {
				private static final String TITLE = "Tracks";
				{
					sequenceListTableModel.selectionModel.addListSelectionListener(
						new ListSelectionListener() {
							@Override
							public void valueChanged(ListSelectionEvent e) {
								if( e.getValueIsAdjusting() )
									return;
								int index = sequenceListTableModel.selectionModel.getMinSelectionIndex();
								String text = TITLE;
								if( index >= 0 )
									text = String.format(text+" - MIDI file No.%d", index);
								setText(text);
							}
						}
					);
					setText(TITLE);
				}
			});
			add(Box.createRigidArea(new Dimension(0, 5)));
			add(new JScrollPane(trackListTableView = new JTable(
				sequenceTrackListTableModel, null,
				trackSelectionModel = new DefaultListSelectionModel() {{
					setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
					addListSelectionListener(new ListSelectionListener() {
						@Override
						public void valueChanged(ListSelectionEvent e) {
							if( e.getValueIsAdjusting() )
								return;
							SequenceTrackListTableModel sequenceModel =
								sequenceListTableModel.getSelectedSequenceModel();
							if( sequenceModel == null || isSelectionEmpty() ) {
								trackEventListTableView.setModel(new TrackEventListTableModel());
							}
							else {
								TrackEventListTableModel trackModel = sequenceModel.getTrackModel(getMinSelectionIndex());
								if( trackModel == null ) {
									trackEventListTableView.setModel(new TrackEventListTableModel());
								}
								else {
									trackEventListTableView.setModel(trackModel);
									TableColumnModel tcm = trackEventListTableView.getColumnModel();
									trackModel.sizeColumnWidthToFit(tcm);
									TableColumn midiMessageColumn = tcm.getColumn(TrackEventListTableModel.Column.MESSAGE.ordinal());
									midiMessageColumn.setCellEditor(eventCellEditor);
								}
							}
							updateButtonStatus();
						}
					});
				}}
			) {{
				// 録音対象のMIDIチャンネルをコンボボックスで選択できるよう、
				// セルエディタを差し替える。
				getColumnModel().getColumn(
					SequenceTrackListTableModel.Column.RECORD_CHANNEL.ordinal()
				).setCellEditor(
					new DefaultCellEditor(new JComboBox<String>() {{
						addItem("OFF");
						for(int i=1; i <= MIDISpec.MAX_CHANNELS; i++)
							addItem(String.format("%d", i));
						addItem("ALL");
					}} )
				);
				// デフォルトでは、データモデルが差し替えられると列が再作成される。
				// しかしこれでは、シーケンスモデルを差し替えたとたん、
				// せっかく差し替えたセルエディタがデフォルトに戻ってしまう。
				//
				// そこで、一度列データモデルが自動作成されたら、
				// 以後は自動作成しないようにする。
				setAutoCreateColumnsFromModel(false);
			}}));
			add(Box.createRigidArea(new Dimension(0, 5)));
			add(new JPanel() {{
				add(new JButton(addTrackAction) {{ setMargin(ZERO_INSETS); }});
				add(new JButton(deleteTrackAction) {{ setMargin(ZERO_INSETS); }});
			}});
		}};
		JPanel eventListPanel = new JPanel() {{
			add(new MidiEventsLabel());
			add(scrollableEventTableView = new JScrollPane(
				trackEventListTableView = new JTable(
					new TrackEventListTableModel(),
					null,
					eventSelectionModel
				) {{
					setAutoCreateColumnsFromModel(false);
				}}
			));
			add(new JPanel() {{
				add(pairNoteCheckbox);
				add(new JButton(eventCellEditor.queryJumpEventAction) {{
					setMargin(ZERO_INSETS);
				}});
				add(new JButton(eventCellEditor.queryAddEventAction) {{
					setMargin(ZERO_INSETS);
				}});
				add(new JButton(copyEventAction) {{ setMargin(ZERO_INSETS); }});
				add(new JButton(cutEventAction) {{ setMargin(ZERO_INSETS); }});
				add(new JButton(eventCellEditor.queryPasteEventAction) {{
					setMargin(ZERO_INSETS);
				}});
				add(new JButton(deleteEventAction) {{ setMargin(ZERO_INSETS); }});
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
		updateButtonStatus();
	}

	/**
	 * ボタン状態の更新
	 */
	public void updateButtonStatus() {
		SequenceTrackListTableModel
			sequenceModel = sequenceListTableModel.getSelectedSequenceModel();
		if(sequenceModel == null)
			trackListTableView.setModel(sequenceListTableModel.emptySequenceModel);
		else
			trackListTableView.setModel(sequenceModel);
		addTrackAction.setEnabled(sequenceModel != null);
		boolean isTrackSelected = (
			! trackSelectionModel.isSelectionEmpty()
			&&
			sequenceModel != null && sequenceModel.getRowCount() > 0
		);
		deleteTrackAction.setEnabled(isTrackSelected);
		//
		TableModel tm = trackEventListTableView.getModel();
		if( ! (tm instanceof TrackEventListTableModel) )
			return;
		TrackEventListTableModel trackTableModel = (TrackEventListTableModel)tm;
		boolean isEventSelected = (
			isTrackSelected &&
			! eventSelectionModel.isSelectionEmpty() &&
			trackTableModel != null && trackTableModel.getRowCount() > 0
		);
		copyEventAction.setEnabled(isEventSelected);
		deleteEventAction.setEnabled(isEventSelected);
		cutEventAction.setEnabled(isEventSelected);
		eventCellEditor.queryJumpEventAction.setEnabled(
			trackTableModel != null && isTrackSelected
		);
		eventCellEditor.queryAddEventAction.setEnabled(
			trackTableModel != null && isTrackSelected
		);
		eventCellEditor.queryPasteEventAction.setEnabled(
			trackTableModel != null && isTrackSelected &&
			copiedEventsToPaste != null && copiedEventsToPaste.length > 0
		);
	}
	/**
	 * MIDIシーケンスを追加します。
	 * シーケンサーが停止中の場合、追加したシーケンスから再生を開始します。
	 * @param sequence MIDIシーケンス
	 * @return 追加先インデックス（先頭が 0）
	 */
	public int addSequenceAndPlay(Sequence sequence) {
		int lastIndex = sequenceListTableModel.addSequence(sequence,"");
		if( ! sequenceListTableModel.sequencerModel.getSequencer().isRunning() ) {
			sequenceListTableModel.loadToSequencer(lastIndex);
			sequenceListTableModel.sequencerModel.start();
		}
		return lastIndex;
	}
	/**
	 * バイト列とファイル名からMIDIシーケンスを追加します。
	 * バイト列が null の場合、空のMIDIシーケンスを追加します。
	 * @param data バイト列
	 * @param filename ファイル名
	 * @return 追加先インデックス（先頭が 0、失敗した場合は -1）
	 */
	public int addSequence(byte[] data, String filename) {
		if( data == null ) {
			return sequenceListTableModel.addDefaultSequence();
		}
		int lastIndex;
		try (InputStream in = new ByteArrayInputStream(data)) {
			Sequence seq = MidiSystem.getSequence(in);
			lastIndex = sequenceListTableModel.addSequence(seq, filename);
		} catch( IOException|InvalidMidiDataException e ) {
			showWarning(e.getMessage());
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
		Sequence seq = null;
		String filename = null;
		try {
			URI uri = new URI(midiFileUrl);
			URL url = uri.toURL();
			seq = MidiSystem.getSequence(url);
			filename = url.getFile().replaceFirst("^.*/","");
		} catch( URISyntaxException|IOException|InvalidMidiDataException e ) {
			showWarning(e.getMessage());
		} catch( AccessControlException e ) {
			showError(e.getMessage());
			e.printStackTrace();
		}
		if( seq == null ) return -1;
		return sequenceListTableModel.addSequence(seq, filename);
	}

	/**
	 * 複数のMIDIファイルを読み込み、再生されていなかったら再生します。
	 * すでに再生されていた場合、このエディタダイアログを表示します。
	 *
	 * @param fileList 読み込むMIDIファイルのリスト
	 */
	public void loadAndPlay(List<File> fileList) {
		int firstIndex = -1;
		try {
			firstIndex = sequenceListTableModel.addSequences(fileList);
		} catch( IOException|InvalidMidiDataException e ) {
			showWarning(e.getMessage());
		} catch( AccessControlException e ) {
			showError(e.getMessage());
			e.printStackTrace();
		}
		if(sequenceListTableModel.sequencerModel.getSequencer().isRunning()) {
			open();
		}
		else if( firstIndex >= 0 ) {
			sequenceListTableModel.loadToSequencer(firstIndex);
			sequenceListTableModel.sequencerModel.start();
		}
	}
	/**
	 * 選択されているシーケンスが、
	 * ユーザ操作により録音可能な設定になったかどうか調べます。
	 * @return 選択されているシーケンスが録音可能な設定ならtrue
	 */
	public boolean isRecordable() {
		SequenceTrackListTableModel sequenceTableModel =
			sequenceListTableModel.getSelectedSequenceModel();
		return sequenceTableModel == null ? false : sequenceTableModel.isRecordable();
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
	private JLabel titleLabel;
	private JSlider slider;
	public SequencerSpeedSlider(BoundedRangeModel model) {
		add(titleLabel = new JLabel("Speed:"));
		add(slider = new JSlider(model){{
			setPaintTicks(true);
			setMajorTickSpacing(12);
			setMinorTickSpacing(1);
			setVisible(false);
		}});
		add(new JComboBox<String>(items) {{
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
		}});
	}
}


/**
 * プレイリスト（MIDIシーケンスリスト）のテーブルデータモデル
 */
class SequenceListTableModel extends AbstractTableModel
	implements MetaEventListener
{
	/**
	 * 新しいプレイリストのテーブルモデルを構築します。
	 * @param sequencerModel MIDIシーケンサーモデル
	 */
	public SequenceListTableModel(MidiSequencerModel sequencerModel) {
		this.sequencerModel = sequencerModel;
		sequencerModel.addChangeListener(secondPosition);
		sequencerModel.getSequencer().addMetaEventListener(this);
	}
	/**
	 * MIDIシーケンサモデル
	 */
	MidiSequencerModel sequencerModel;
	/**
	 * シーケンスリスト
	 */
	List<SequenceTrackListTableModel> sequenceList = new Vector<>();
	/**
	 * 行の選択状態
	 */
	ListSelectionModel selectionModel = new DefaultListSelectionModel() {{
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
	}};
	/**
	 * 空のシーケンスモデル
	 */
	SequenceTrackListTableModel emptySequenceModel =
		new SequenceTrackListTableModel(this);
	/**
	 * 行が選択されているときだけイネーブルになるアクション
	 */
	public abstract class SelectedSequenceAction extends AbstractAction
		implements ListSelectionListener
	{
		public SelectedSequenceAction(String name, Icon icon, String tooltip) {
			super(name,icon); init(tooltip);
		}
		public SelectedSequenceAction(String name, String tooltip) {
			super(name); init(tooltip);
		}
		@Override
		public void valueChanged(ListSelectionEvent e) {
			if( e.getValueIsAdjusting() ) return;
			setEnebledBySelection();
		}
		protected void setEnebledBySelection() {
			int index = selectionModel.getMinSelectionIndex();
			setEnabled(index >= 0);
		}
		private void init(String tooltip) {
			putValue(Action.SHORT_DESCRIPTION, tooltip);
			selectionModel.addListSelectionListener(this);
			setEnebledBySelection();
		}
	}
	/**
	 * 選択されたシーケンスへジャンプするアクション
	 */
	public Action loadToSequencerAction = new SelectedSequenceAction(
		"Load to sequencer",
		"Load selected MIDI sequence to sequencer - 選択した曲をシーケンサへロード"
	) {
		@Override
		public void actionPerformed(ActionEvent e) {
			loadToSequencer(selectionModel.getMinSelectionIndex());
		}
	};
	/**
	 * 繰り返し再生ON/OFF切り替えアクション
	 */
	public Action toggleRepeatAction = new AbstractAction() {
		{
			putValue(SHORT_DESCRIPTION, "Repeat - 繰り返し再生");
			putValue(LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.REPEAT_ICON));
			putValue(SELECTED_KEY, false);
		}
		@Override
		public void actionPerformed(ActionEvent event) { }
	};
	/**
	 * {@inheritDoc}
	 *
	 * <p>EOT (End Of Track、type==0x2F) を受信したときの処理です。
	 * 通常はシーケンサの EDT から起動されるため、
	 * Swing の EDT へ振りなおします。
	 * </p>
	 */
	@Override
	public void meta(MetaMessage msg) {
		if( msg.getType() == 0x2F ) {
			if( ! SwingUtilities.isEventDispatchThread() ) {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() { goNext(); }
				});
				return;
			}
			goNext();
		}
	}
	/**
	 * 次の曲へ進みます。
	 *
	 * <p>リピートモードの場合は同じ曲をもう一度再生、
	 * そうでない場合は次の曲へ進んで再生します。
	 * 次の曲がなければ、そこで停止します。
	 * いずれの場合も局の先頭へ戻ります。
	 * </p>
	 */
	private void goNext() {
		sequencerModel.getSequencer().setMicrosecondPosition(0);
		if( (Boolean)toggleRepeatAction.getValue(Action.SELECTED_KEY) || loadNext(1) ) {
			sequencerModel.start();
		}
		else {
			sequencerModel.stop();
		}
	}
	/**
	 * 再生中のシーケンサーの秒位置
	 */
	private SecondPosition secondPosition = new SecondPosition();
	/**
	 * 再生中のシーケンサーの秒位置
	 */
	private class SecondPosition implements ChangeListener {
		private int value = 0;
		/**
		 * 再生中のシーケンサーの秒位置が変わったときに表示を更新します。
		 */
		@Override
		public void stateChanged(ChangeEvent e) {
			if( e.getSource() instanceof MidiSequencerModel ) {
				MidiSequencerModel m = (MidiSequencerModel)e.getSource();
				int newValue = m.getValue() / 1000;
				if(value == newValue)
					return;
				// 秒が変わったときだけ更新（小数点以下は無視）
				value = newValue;
				fireTableCellUpdated(
					indexOfSequenceOnSequencer(), Column.SEQ_POSITION.ordinal()
				);
			}
		}
		@Override
		public String toString() {
			return String.format("%02d:%02d", value/60, value%60);
		}
	}
	/**
	 * 曲の先頭または前の曲へ戻るアクション
	 */
	public Action moveToTopAction = new AbstractAction() {
		{
			putValue(SHORT_DESCRIPTION,
				"Move to top or previous song - 曲の先頭または前の曲へ戻る"
			);
			putValue(LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.TOP_ICON));
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
			putValue(SHORT_DESCRIPTION, "Move to next song - 次の曲へ進む");
			putValue(LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.BOTTOM_ICON));
		}
		public void actionPerformed(ActionEvent event) {
			if(loadNext(1)) sequencerModel.setValue(0);
		}
	};

	/**
	 * 列の列挙型
	 */
	public enum Column {
		/** MIDIシーケンスの番号 */
		SEQ_NUMBER("No.", Integer.class),
		/** 変更済みフラグ */
		MODIFIED("Modified", Boolean.class),
		/** 再生ボタン */
		SEQ_PLAY("Sequencer", String.class) {
			@Override
			public boolean isCellEditable() { return true; }
		},
		/** 再生中の時間位置（分：秒） */
		SEQ_POSITION("Position", String.class),
		/** シーケンスの時間長（分：秒） */
		SEQ_LENGTH("Length", String.class),
		/** ファイル名 */
		FILENAME("Filename", String.class) {
			@Override
			public boolean isCellEditable() { return true; }
		},
		/** シーケンス名（最初のトラックの名前） */
		SEQ_NAME("Sequence name", String.class) {
			@Override
			public boolean isCellEditable() { return true; }
		},
		/** タイミング解像度 */
		RESOLUTION("Resolution", Integer.class),
		/** トラック数 */
		TRACKS("Tracks", Integer.class),
		/** タイミング分割形式 */
		DIVISION_TYPE("DivType", String.class);
		String title;
		Class<?> columnClass;
		/**
		 * 列の識別子を構築します。
		 * @param title 列のタイトル
		 * @param columnClass 列のクラス
		 */
		private Column(String title, Class<?> columnClass) {
			this.title = title;
			this.columnClass = columnClass;
		}
		public boolean isCellEditable() { return false; }
	}

	@Override
	public int getRowCount() { return sequenceList.size(); }
	@Override
	public int getColumnCount() { return Column.values().length; }
	@Override
	public String getColumnName(int column) {
		return Column.values()[column].title;
	}
	@Override
	public Class<?> getColumnClass(int column) {
		return Column.values()[column].columnClass;
	}
	@Override
	public boolean isCellEditable(int row, int column) {
		return Column.values()[column].isCellEditable();
	}
	@Override
	public Object getValueAt(int row, int column) {
		Column c = Column.values()[column];
		if( c == Column.SEQ_NUMBER ) return row;
		SequenceTrackListTableModel sequenceModel = sequenceList.get(row);
		switch(c) {
		case MODIFIED: return sequenceModel.isModified();
		case DIVISION_TYPE: {
			float divType = sequenceModel.getSequence().getDivisionType();
			if( divType == Sequence.PPQ ) return "PPQ";
			else if( divType == Sequence.SMPTE_24 ) return "SMPTE_24";
			else if( divType == Sequence.SMPTE_25 ) return "SMPTE_25";
			else if( divType == Sequence.SMPTE_30 ) return "SMPTE_30";
			else if( divType == Sequence.SMPTE_30DROP ) return "SMPTE_30DROP";
			else return "[Unknown]";
		}
		case RESOLUTION: return sequenceModel.getSequence().getResolution();
		case TRACKS: return sequenceModel.getSequence().getTracks().length;
		case SEQ_POSITION: return sequenceModel.isOnSequencer() ? secondPosition : "";
		case SEQ_LENGTH: {
			long usec = sequenceModel.getSequence().getMicrosecondLength();
			int sec = (int)( (usec < 0 ? usec += 0x100000000L : usec) / 1000L / 1000L );
			return String.format( "%02d:%02d", sec/60, sec%60 );
		}
		case FILENAME: {
			String filename = sequenceModel.getFilename();
			return filename == null ? "" : filename;
		}
		case SEQ_NAME: {
			String name = sequenceModel.toString();
			return name == null ? "" : name;
		}
		default: return "";
		}
	}
	@Override
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
			fireTableCellUpdated(row, column);
			break;
		default:
			break;
		}
	}
	/**
	 * このプレイリストに読み込まれた全シーケンスの合計時間長を返します。
	 * @return 全シーケンスの合計時間長 [秒]
	 */
	public int getTotalSeconds() {
		int total = 0;
		long usec;
		for( SequenceTrackListTableModel m : sequenceList ) {
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
		for( SequenceTrackListTableModel m : sequenceList ) {
			if( m.isModified() ) return true;
		}
		return false;
	}
	/**
	 * 選択したシーケンスに未保存の修正内容があることを記録します。
	 * @param selModel 選択状態
	 * @param isModified 未保存の修正内容があるときtrue
	 */
	public void setModified(boolean isModified) {
		int minIndex = selectionModel.getMinSelectionIndex();
		int maxIndex = selectionModel.getMaxSelectionIndex();
		for( int i = minIndex; i <= maxIndex; i++ ) {
			if( selectionModel.isSelectedIndex(i) ) {
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
	public SequenceTrackListTableModel getSelectedSequenceModel() {
		if( selectionModel.isSelectionEmpty() )
			return null;
		int selectedIndex = selectionModel.getMinSelectionIndex();
		if( selectedIndex >= sequenceList.size() )
			return null;
		return sequenceList.get(selectedIndex);
	}
	/**
	 * 指定されたシーケンスが修正されたことを通知します。
	 * @param sequenceTableModel MIDIシーケンスモデル
	 */
	public void fireSequenceModified(SequenceTrackListTableModel sequenceTableModel) {
		int index = sequenceList.indexOf(sequenceTableModel);
		if( index < 0 )
			return;
		sequenceTableModel.setModified(true);
		fireTableRowsUpdated(index, index);
	}
	/**
	 * 指定された選択範囲のシーケンスが変更されたことを通知します。
	 * @param selectionModel 選択状態
	 */
	public void fireSelectedSequenceChanged() {
		if( selectionModel.isSelectionEmpty() )
			return;
		int minIndex = selectionModel.getMinSelectionIndex();
		int maxIndex = selectionModel.getMaxSelectionIndex();
		for( int index = minIndex; index <= maxIndex; index++ ) {
			sequenceList.get(index).setModified(true);
		}
		fireTableRowsUpdated(minIndex, maxIndex);
	}
	/**
	 * MIDIシーケンスを追加します。
	 * @param seq MIDIシーケンス
	 * @param filename ファイル名
	 * @return 追加されたシーケンスのインデックス（先頭が 0）
	 */
	public int addSequence(Sequence seq, String filename) {
		sequenceList.add(new SequenceTrackListTableModel(this, seq, filename));
		int lastIndex = sequenceList.size() - 1;
		fireTableRowsInserted(lastIndex, lastIndex);
		return lastIndex;
	}
	/**
	 * デフォルトの内容でMIDIシーケンスを作成して追加します。
	 * @return 追加されたMIDIシーケンスのインデックス（先頭が 0）
	 */
	public int addDefaultSequence() {
		Sequence seq = (new Music.ChordProgression()).toMidiSequence();
		return seq == null ? -1 : addSequence(seq,null);
	}
	/**
	 * MIDIファイルを追加します。
	 * ファイルが null の場合、空のMIDIシーケンスを追加します。
	 * @param midiFile MIDIファイル
	 * @return 追加先インデックス（先頭が 0）
	 * @throws InvalidMidiDataException ファイル内のMIDIデータが正しくない場合
	 * @throws IOException ファイル入出力に失敗した場合
	 */
	public int addSequence(File midiFile) throws InvalidMidiDataException, IOException {
		if( midiFile == null ) return addDefaultSequence();
		int lastIndex;
		try (FileInputStream in = new FileInputStream(midiFile)) {
			Sequence seq = MidiSystem.getSequence(in);
			String filename = midiFile.getName();
			lastIndex = addSequence(seq, filename);
		} catch( InvalidMidiDataException|IOException e ) {
			throw e;
		}
		return lastIndex;
	}
	/**
	 * 複数のMIDIファイルを追加します。
	 * @param fileList 追加するMIDIファイルのリスト
	 * @return 追加先の最初のインデックス（先頭が 0、追加されなかった場合は -1）
	 * @throws InvalidMidiDataException ファイル内のMIDIデータが正しくない場合
	 * @throws IOException ファイル入出力に失敗した場合
	 */
	public int addSequences(List<File> fileList) throws InvalidMidiDataException, IOException {
		int firstIndex = -1;
		for( File file : fileList ) {
			int lastIndex = addSequence(file);
			if( firstIndex == -1 )
				firstIndex = lastIndex;
		}
		return firstIndex;
	}
	/**
	 * 選択したシーケンスを除去します。
	 * @param listSelectionModel 選択状態
	 */
	public void removeSelectedSequence() {
		if( selectionModel.isSelectionEmpty() )
			return;
		int selectedIndex = selectionModel.getMinSelectionIndex();
		if( sequenceList.remove(selectedIndex).isOnSequencer() ) {
			// 削除したシーケンスが
			// シーケンサーにロード済みだった場合、アンロードする。
			sequencerModel.setSequenceTrackListTableModel(null);
		}
		fireTableRowsDeleted(selectedIndex, selectedIndex);
	}
	/**
	 * 指定したインデックス位置のシーケンスをシーケンサーにロードします。
	 * @param index シーケンスのインデックス位置（-1 を指定するとアンロードされます）
	 */
	public void loadToSequencer(int index) {
		SequenceTrackListTableModel oldSeq = sequencerModel.getSequenceTableModel();
		SequenceTrackListTableModel newSeq = (index < 0 ? null : sequenceList.get(index));
		if(oldSeq == newSeq)
			return;
		sequencerModel.setSequenceTrackListTableModel(newSeq);
		int columnIndices[] = {
			Column.SEQ_PLAY.ordinal(),
			Column.SEQ_POSITION.ordinal(),
		};
		if( oldSeq != null ) {
			int oldIndex = sequenceList.indexOf(oldSeq);
			for( int columnIndex : columnIndices )
				fireTableCellUpdated(oldIndex, columnIndex);
		}
		if( newSeq != null )
			for( int columnIndex : columnIndices )
				fireTableCellUpdated(index, columnIndex);
	}
	/**
	 * 現在シーケンサにロードされているシーケンスのインデックスを返します。
	 * ロードされていない場合は -1 を返します。
	 * @return 現在シーケンサにロードされているシーケンスのインデックス
	 */
	public int indexOfSequenceOnSequencer() {
		return sequenceList.indexOf(sequencerModel.getSequenceTableModel());
	}
	/**
	 * 引数で示された数だけ次へ進めたシーケンスをロードします。
	 * @param offset 進みたいシーケンス数
	 * @return 成功したらtrue
	 */
	public boolean loadNext(int offset) {
		int loadedIndex = indexOfSequenceOnSequencer();
		int index = (loadedIndex < 0 ? 0 : loadedIndex + offset);
		if( index < 0 || index >= sequenceList.size() )
			return false;
		loadToSequencer(index);
		return true;
	}
}

/**
 * MIDIシーケンス（トラックリスト）のテーブルデータモデル
 */
class SequenceTrackListTableModel extends AbstractTableModel {
	/**
	 * 列の列挙型
	 */
	public enum Column {
		/** トラック番号 */
		TRACK_NUMBER("No.", Integer.class),
		/** イベント数 */
		EVENTS("Events", Integer.class),
		/** Mute */
		MUTE("Mute", Boolean.class),
		/** Solo */
		SOLO("Solo", Boolean.class),
		/** 録音するMIDIチャンネル */
		RECORD_CHANNEL("RecCh", String.class),
		/** MIDIチャンネル */
		CHANNEL("Ch", String.class),
		/** トラック名 */
		TRACK_NAME("Track name", String.class);
		String title;
		Class<?> columnClass;
		/**
		 * 列の識別子を構築します。
		 * @param title 列のタイトル
		 * @param widthRatio 幅の割合
		 * @param columnClass 列のクラス
		 */
		private Column(String title, Class<?> columnClass) {
			this.title = title;
			this.columnClass = columnClass;
		}
	}
	/**
	 * 親のプレイリスト
	 */
	SequenceListTableModel sequenceListTableModel;
	/**
	 * ラップされたMIDIシーケンス
	 */
	private Sequence sequence;
	/**
	 * ラップされたMIDIシーケンスのtickインデックス
	 */
	private SequenceTickIndex sequenceTickIndex;
	/**
	 * MIDIファイル名
	 */
	private String filename = "";
	/**
	 * トラックリスト
	 */
	private List<TrackEventListTableModel> trackModelList = new ArrayList<>();
	/**
	 * 空の {@link SequenceTrackListTableModel} を構築します。
	 * @param sequenceListTableModel 親のプレイリスト
	 */
	public SequenceTrackListTableModel(SequenceListTableModel sequenceListTableModel) {
		this.sequenceListTableModel = sequenceListTableModel;
	}
	/**
	 * MIDIシーケンスとファイル名から {@link SequenceTrackListTableModel} を構築します。
	 * @param sequenceListTableModel 親のプレイリスト
	 * @param sequence MIDIシーケンス
	 * @param filename ファイル名
	 */
	public SequenceTrackListTableModel(
		SequenceListTableModel sequenceListTableModel,
		Sequence sequence,
		String filename
	) {
		this(sequenceListTableModel);
		setSequence(sequence);
		setFilename(filename);
	}
	@Override
	public int getRowCount() {
		return sequence == null ? 0 : sequence.getTracks().length;
	}
	@Override
	public int getColumnCount() {
		return Column.values().length;
	}
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
		case SOLO: if( ! isOnSequencer() ) return String.class;
			// FALLTHROUGH
		default: return c.columnClass;
		}
	}
	@Override
	public Object getValueAt(int row, int column) {
		Column c = Column.values()[column];
		switch(c) {
		case TRACK_NUMBER: return row;
		case EVENTS: return sequence.getTracks()[row].size();
		case MUTE:
			return isOnSequencer() ? getSequencer().getTrackMute(row) : "";
		case SOLO:
			return isOnSequencer() ? getSequencer().getTrackSolo(row) : "";
		case RECORD_CHANNEL:
			return isOnSequencer() ? trackModelList.get(row).getRecordingChannel() : "";
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
		case RECORD_CHANNEL: return isOnSequencer();
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
			TrackEventListTableModel trackTableModel = trackModelList.get(row);
			if( ch == trackTableModel.getChannel() ) break;
			trackTableModel.setChannel(ch);
			setModified(true);
			fireTableCellUpdated(row, Column.EVENTS.ordinal());
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
	private void setSequence(Sequence sequence) {
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
			for(Track track : tracks) {
				trackModelList.add(new TrackEventListTableModel(track, this));
			}
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
		sequenceListTableModel.fireSequenceModified(this);
	}
	/**
	 * 指定のインデックスのトラックモデルを返します。
	 * @param index トラックのインデックス
	 * @return トラックモデル（見つからない場合null）
	 */
	public TrackEventListTableModel getTrackModel(int index) {
		Track tracks[] = sequence.getTracks();
		if( tracks.length != 0 ) {
			Track track = tracks[index];
			for( TrackEventListTableModel model : trackModelList )
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
		trackModelList.add(new TrackEventListTableModel(sequence.createTrack(), this));
		int lastRow = sequence.getTracks().length - 1;
		fireTableRowsInserted(lastRow, lastRow);
		// 親のプレイリストのトラック数表示を更新
		sequenceListTableModel.fireSelectedSequenceChanged();
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
		fireTableRowsDeleted(minIndex, maxIndex);
		// 親のプレイリストのトラック数表示を更新
		sequenceListTableModel.fireSelectedSequenceChanged();
	}
	/**
	 * MIDIシーケンサを返します。
	 * @return MIDIシーケンサ
	 */
	public Sequencer getSequencer() {
		return sequenceListTableModel.sequencerModel.getSequencer();
	}
	/**
	 * このシーケンスモデルのシーケンスをシーケンサーが操作しているか調べます。
	 * @return シーケンサーが操作していたらtrue
	 */
	public boolean isOnSequencer() {
		return sequence == getSequencer().getSequence();
	}
	/**
	 * 録音可能かどうかを返します。
	 *
	 * <p>シーケンサーにロード済みで、
	 * かつ録音しようとしているチャンネルの設定されたトラックが一つでもあれば、
	 * 録音可能です。
	 * </p>
	 * @return 録音可能であればtrue
	 */
	public boolean isRecordable() {
		if( isOnSequencer() ) {
			int rowCount = getRowCount();
			int col = Column.RECORD_CHANNEL.ordinal();
			for( int row=0; row < rowCount; row++ )
				if( ! "OFF".equals(getValueAt(row, col)) ) return true;
		}
		return false;
	}
}

/**
 * MIDIトラック（MIDIイベントリスト）テーブルモデル
 */
class TrackEventListTableModel extends AbstractTableModel {
	/**
	 * 列
	 */
	public enum Column {
		/** MIDIイベント番号 */
		EVENT_NUMBER("No.", 30, Integer.class),
		/** tick位置 */
		TICK_POSITION("TickPos.", 40, Long.class),
		/** tick位置に対応する小節 */
		MEASURE_POSITION("Measure", 20, Integer.class),
		/** tick位置に対応する拍 */
		BEAT_POSITION("Beat", 20, Integer.class),
		/** tick位置に対応する余剰tick（拍に収まらずに余ったtick数） */
		EXTRA_TICK_POSITION("ExTick", 20, Integer.class),
		/** MIDIメッセージ */
		MESSAGE("MIDI Message", 280, String.class);
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
	/**
	 * ラップされているMIDIトラック
	 */
	private Track track;
	/**
	 * 親のシーケンスモデル
	 */
	private SequenceTrackListTableModel parent;
	/**
	 * 空のMIDIトラックモデルを構築します。
	 */
	public TrackEventListTableModel() { }
	/**
	 * シーケンスに連動する空のMIDIトラックモデルを構築します。
	 * @parent 親のシーケンステーブルモデル
	 */
	public TrackEventListTableModel(SequenceTrackListTableModel parent) {
		this.parent = parent;
	}
	/**
	 * シーケンスを親にして、その特定のトラックに連動する
	 * MIDIトラックモデルを構築します。
	 *
	 * @param track ラップするMIDIトラック
	 * @param parent 親のシーケンスモデル
	 */
	public TrackEventListTableModel(Track track, SequenceTrackListTableModel parent) {
		this.track = track;
		this.parent = parent;
	}
	@Override
	public int getRowCount() {
		return track == null ? 0 : track.size();
	}
	@Override
	public int getColumnCount() {
		return Column.values().length;
	}
	/**
	 * 列名を返します。
	 */
	@Override
	public String getColumnName(int column) {
		return Column.values()[column].title;
	}
	/**
	 * 列のクラスを返します。
	 */
	@Override
	public Class<?> getColumnClass(int column) {
		return Column.values()[column].columnClass;
	}
	@Override
	public Object getValueAt(int row, int column) {
		switch(Column.values()[column]) {
		case EVENT_NUMBER: return row;
		case TICK_POSITION: return track.get(row).getTick();
		case MEASURE_POSITION:
			return parent.getSequenceTickIndex().tickToMeasure(track.get(row).getTick()) + 1;
		case BEAT_POSITION:
			parent.getSequenceTickIndex().tickToMeasure(track.get(row).getTick());
			return parent.getSequenceTickIndex().lastBeat + 1;
		case EXTRA_TICK_POSITION:
			parent.getSequenceTickIndex().tickToMeasure(track.get(row).getTick());
			return parent.getSequenceTickIndex().lastExtraTick;
		case MESSAGE: return msgToString(track.get(row).getMessage());
		default: return "";
		}
	}
	/**
	 * セルを編集できるときtrue、編集できないときfalseを返します。
	 */
	@Override
	public boolean isCellEditable(int row, int column) {
		switch(Column.values()[column]) {
		case TICK_POSITION:
		case MEASURE_POSITION:
		case BEAT_POSITION:
		case EXTRA_TICK_POSITION:
		case MESSAGE: return true;
		default: return false;
		}
	}
	/**
	 * セルの値を変更します。
	 */
	@Override
	public void setValueAt(Object value, int row, int column) {
		long newTick;
		switch(Column.values()[column]) {
		case TICK_POSITION: newTick = (Long)value; break;
		case MEASURE_POSITION:
			newTick = parent.getSequenceTickIndex().measureToTick(
				(Integer)value - 1,
				(Integer)getValueAt( row, Column.BEAT_POSITION.ordinal() ) - 1,
				(Integer)getValueAt( row, Column.TICK_POSITION.ordinal() )
			);
			break;
		case BEAT_POSITION:
			newTick = parent.getSequenceTickIndex().measureToTick(
				(Integer)getValueAt( row, Column.MEASURE_POSITION.ordinal() ) - 1,
				(Integer)value - 1,
				(Integer)getValueAt( row, Column.EXTRA_TICK_POSITION.ordinal() )
			);
			break;
		case EXTRA_TICK_POSITION:
			newTick = parent.getSequenceTickIndex().measureToTick(
				(Integer)getValueAt( row, Column.MEASURE_POSITION.ordinal() ) - 1,
				(Integer)getValueAt( row, Column.BEAT_POSITION.ordinal() ) - 1,
				(Integer)value
			);
			break;
		default: return;
		}
		MidiEvent oldMidiEvent = track.get(row);
		if( oldMidiEvent.getTick() == newTick ) {
			return;
		}
		MidiMessage msg = oldMidiEvent.getMessage();
		MidiEvent newMidiEvent = new MidiEvent(msg,newTick);
		track.remove(oldMidiEvent);
		track.add(newMidiEvent);
		fireTableDataChanged();
		if( MIDISpec.isEOT(msg) ) {
			// EOTの場所が変わると曲の長さが変わるので、親モデルへ通知する。
			parent.sequenceListTableModel.fireSequenceModified(parent);
		}
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
	 * MIDIトラックを返します。
	 * @return MIDIトラック
	 */
	public Track getTrack() { return track; }
	/**
	 * トラック名を返します。
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
		parent.sequenceListTableModel.fireSequenceModified(parent);
		fireTableDataChanged();
		return true;
	}
	private String recordingChannel = "OFF";
	/**
	 * 録音中のMIDIチャンネルを返します。
	 * @return 録音中のMIDIチャンネル
	 */
	public String getRecordingChannel() { return recordingChannel; }
	/**
	 * 録音中のMIDIチャンネルを設定します。
	 * @param recordingChannel 録音中のMIDIチャンネル
	 */
	public void setRecordingChannel(String recordingChannel) {
		Sequencer sequencer = parent.getSequencer();
		if( recordingChannel.equals("OFF") ) {
			sequencer.recordDisable( track );
		}
		else if( recordingChannel.equals("ALL") ) {
			sequencer.recordEnable( track, -1 );
		}
		else {
			try {
				int ch = Integer.decode(recordingChannel).intValue() - 1;
				sequencer.recordEnable( track, ch );
			} catch( NumberFormatException nfe ) {
				sequencer.recordDisable( track );
				this.recordingChannel = "OFF";
				return;
			}
		}
		this.recordingChannel = recordingChannel;
	}
	/**
	 * このトラックの対象MIDIチャンネルを返します。
	 * <p>全てのチャンネルメッセージが同じMIDIチャンネルの場合、
	 * そのMIDIチャンネルを返します。
	 * MIDIチャンネルの異なるチャンネルメッセージが一つでも含まれていた場合、
	 * -1 を返します。
	 * </p>
	 * @return 対象MIDIチャンネル（不統一の場合 -1）
	 */
	public int getChannel() {
		int prevCh = -1;
		int trackSize = track.size();
		for( int index=0; index < trackSize; index++ ) {
			MidiMessage msg = track.get(index).getMessage();
			if( ! (msg instanceof ShortMessage) )
				continue;
			ShortMessage smsg = (ShortMessage)msg;
			if( ! MIDISpec.isChannelMessage(smsg) )
				continue;
			int ch = smsg.getChannel();
			if( prevCh >= 0 && prevCh != ch ) {
				return -1;
			}
			prevCh = ch;
		}
		return prevCh;
	}
	/**
	 * 指定されたMIDIチャンネルをすべてのチャンネルメッセージに対して設定します。
	 * @param channel MIDIチャンネル
	 */
	public void setChannel(int channel) {
		int track_size = track.size();
		for( int index=0; index < track_size; index++ ) {
			MidiMessage msg = track.get(index).getMessage();
			if( ! (msg instanceof ShortMessage) )
				continue;
			ShortMessage smsg = (ShortMessage)msg;
			if( ! MIDISpec.isChannelMessage(smsg) )
				continue;
			if( smsg.getChannel() == channel )
				continue;
			try {
				smsg.setMessage(
					smsg.getCommand(), channel,
					smsg.getData1(), smsg.getData2()
				);
			}
			catch( InvalidMidiDataException e ) {
				e.printStackTrace();
			}
			parent.setModified(true);
		}
		parent.fireTrackChanged(track);
		fireTableDataChanged();
	}
	/**
	 * 指定の MIDI tick 位置にあるイベントを二分探索し、
	 * そのイベントの行インデックスを返します。
	 * @param tick MIDI tick
	 * @return 行インデックス
	 */
	public int tickToIndex(long tick) {
		if( track == null )
			return 0;
		int minIndex = 0;
		int maxIndex = track.size() - 1;
		while( minIndex < maxIndex ) {
			int currentIndex = (minIndex + maxIndex) / 2 ;
			long currentTick = track.get(currentIndex).getTick();
			if( tick > currentTick ) {
				minIndex = currentIndex + 1;
			}
			else if( tick < currentTick ) {
				maxIndex = currentIndex - 1;
			}
			else {
				return currentIndex;
			}
		}
		return (minIndex + maxIndex) / 2;
	}
	/**
	 * NoteOn/NoteOff ペアの一方の行インデックスから、
	 * もう一方（ペアの相手）の行インデックスを返します。
	 * @param index 行インデックス
	 * @return ペアを構成する相手の行インデックス（ない場合は -1）
	 */
	public int getIndexOfPartnerFor(int index) {
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
		if( MIDISpec.isTimeSignature(midiEvent.getMessage()) )
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
			if( MIDISpec.isTimeSignature(msg) ) hasTimeSignature = true;
		}
		if( done ) {
			if( hasTimeSignature ) parent.fireTimeSignatureChanged();
			parent.fireTrackChanged(track);
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
		boolean hadTimeSignature = false;
		for( MidiEvent e : midiEvents ) {
			if( MIDISpec.isTimeSignature(e.getMessage()) )
				hadTimeSignature = true;
			track.remove(e);
		}
		if( hadTimeSignature ) parent.fireTimeSignatureChanged();
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
