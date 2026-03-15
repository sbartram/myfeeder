import { useState } from 'react'
import { useBoards, useCreateBoard, useAddArticleToBoard } from '../hooks/useBoards'

interface BoardManagerProps {
  open: boolean
  articleId: number | null
  onClose: () => void
}

export function BoardManager({ open, articleId, onClose }: BoardManagerProps) {
  const { data: boards = [] } = useBoards()
  const createBoard = useCreateBoard()
  const addToBoard = useAddArticleToBoard()
  const [newBoardName, setNewBoardName] = useState('')
  const [showCreate, setShowCreate] = useState(false)

  if (!open || !articleId) return null

  const handleAddToBoard = (boardId: number) => {
    addToBoard.mutate({ boardId, articleId }, { onSuccess: onClose })
  }

  const handleCreateAndAdd = () => {
    if (!newBoardName.trim()) return
    createBoard.mutate(
      { name: newBoardName.trim() },
      {
        onSuccess: (board) => {
          addToBoard.mutate({ boardId: board.id, articleId }, { onSuccess: onClose })
          setNewBoardName('')
          setShowCreate(false)
        },
      }
    )
  }

  return (
    <div className="dialog-overlay" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()} style={{ width: 320 }}>
        <h2>Add to Board</h2>

        {boards.length === 0 && !showCreate && (
          <p style={{ color: '#888', fontSize: 13, marginBottom: 12 }}>
            No boards yet. Create one to start curating articles.
          </p>
        )}

        <div style={{ maxHeight: 200, overflowY: 'auto', marginBottom: 12 }}>
          {boards.map((board) => (
            <div
              key={board.id}
              className="board-picker-item"
              onClick={() => handleAddToBoard(board.id)}
            >
              <span>{board.name}</span>
              {board.description && (
                <span style={{ fontSize: 11, color: '#666' }}>{board.description}</span>
              )}
            </div>
          ))}
        </div>

        {showCreate ? (
          <div>
            <input
              className="dialog-input"
              placeholder="Board name"
              value={newBoardName}
              onChange={(e) => setNewBoardName(e.target.value)}
              autoFocus
              onKeyDown={(e) => e.key === 'Enter' && handleCreateAndAdd()}
            />
            <div className="dialog-actions" style={{ marginTop: 8 }}>
              <button className="btn-secondary" onClick={() => setShowCreate(false)}>Cancel</button>
              <button className="btn-primary" onClick={handleCreateAndAdd}>Create &amp; Add</button>
            </div>
          </div>
        ) : (
          <button className="btn-secondary" onClick={() => setShowCreate(true)} style={{ width: '100%' }}>
            + New Board
          </button>
        )}
      </div>
    </div>
  )
}
