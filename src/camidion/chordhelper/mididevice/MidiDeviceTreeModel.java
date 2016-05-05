package camidion.chordhelper.mididevice;

import java.util.List;

import javax.swing.event.EventListenerList;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * MIDIデバイスツリーモデル
 */
public class MidiDeviceTreeModel implements TreeModel {
	List<MidiConnecterListModel> deviceModelList;
	public MidiDeviceTreeModel(List<MidiConnecterListModel> deviceModelList) {
		this.deviceModelList = deviceModelList;
	}
	@Override
	public Object getRoot() { return "MIDI devices"; }
	@Override
	public Object getChild(Object parent, int index) {
		if( parent == getRoot() ) return MidiDeviceInOutType.values()[index + 1];
		if( parent instanceof MidiDeviceInOutType ) {
			MidiDeviceInOutType ioType = (MidiDeviceInOutType)parent;
			for( MidiConnecterListModel deviceModel : deviceModelList )
				if( deviceModel.getMidiDeviceInOutType() == ioType ) {
					if( index == 0 ) return deviceModel;
					index--;
				}
		}
		return null;
	}
	@Override
	public int getChildCount(Object parent) {
		if( parent == getRoot() ) return MidiDeviceInOutType.values().length - 1;
		int childCount = 0;
		if( parent instanceof MidiDeviceInOutType ) {
			MidiDeviceInOutType ioType = (MidiDeviceInOutType)parent;
			for( MidiConnecterListModel deviceModel : deviceModelList )
				if( deviceModel.getMidiDeviceInOutType() == ioType ) childCount++;
		}
		return childCount;
	}
	@Override
	public int getIndexOfChild(Object parent, Object child) {
		if( parent == getRoot() ) {
			if( child instanceof MidiDeviceInOutType ) {
				MidiDeviceInOutType ioType = (MidiDeviceInOutType)child;
				return ioType.ordinal() - 1;
			}
		}
		if( parent instanceof MidiDeviceInOutType ) {
			MidiDeviceInOutType ioType = (MidiDeviceInOutType)parent;
			int index = 0;
			for( MidiConnecterListModel deviceModel : deviceModelList ) {
				if( deviceModel.getMidiDeviceInOutType() == ioType ) {
					if( deviceModel == child ) return index;
					index++;
				}
			}
		}
		return -1;
	}
	@Override
	public boolean isLeaf(Object node) { return node instanceof MidiConnecterListModel; }
	@Override
	public void valueForPathChanged(TreePath path, Object newValue) {}
	//
	private EventListenerList listenerList = new EventListenerList();
	@Override
	public void addTreeModelListener(TreeModelListener listener) {
		listenerList.add(TreeModelListener.class, listener);
	}
	@Override
	public void removeTreeModelListener(TreeModelListener listener) {
		listenerList.remove(TreeModelListener.class, listener);
	}
	public void fireTreeNodesChanged(
		Object source, Object[] path, int[] childIndices, Object[] children
	) {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==TreeModelListener.class) {
				((TreeModelListener)listeners[i+1]).treeNodesChanged(
					new TreeModelEvent(source,path,childIndices,children)
				);
			}
		}
	}
}