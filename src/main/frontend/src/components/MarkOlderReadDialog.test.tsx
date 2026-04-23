import { render, screen, fireEvent } from '@testing-library/react'
import { beforeEach, describe, it, expect, vi } from 'vitest'
import { MarkOlderReadDialog } from './MarkOlderReadDialog'

describe('MarkOlderReadDialog', () => {
  const defaultProps = {
    open: true,
    feedName: 'Test Feed',
    onConfirm: vi.fn(),
    onClose: vi.fn(),
  }

  beforeEach(() => {
    defaultProps.onConfirm.mockClear()
    defaultProps.onClose.mockClear()
  })

  it('renders preset buttons', () => {
    render(<MarkOlderReadDialog {...defaultProps} />)
    expect(screen.getByText('1 days')).toBeInTheDocument()
    expect(screen.getByText('3 days')).toBeInTheDocument()
    expect(screen.getByText('7 days')).toBeInTheDocument()
    expect(screen.getByText('14 days')).toBeInTheDocument()
  })

  it('calls onConfirm with preset value', () => {
    render(<MarkOlderReadDialog {...defaultProps} />)
    fireEvent.click(screen.getByText('3 days'))
    fireEvent.click(screen.getByText('Mark as read'))
    expect(defaultProps.onConfirm).toHaveBeenCalledWith(3)
  })

  it('accepts custom input', () => {
    render(<MarkOlderReadDialog {...defaultProps} />)
    const input = screen.getByPlaceholderText('Custom')
    fireEvent.change(input, { target: { value: '14' } })
    fireEvent.click(screen.getByText('Mark as read'))
    expect(defaultProps.onConfirm).toHaveBeenCalledWith(14)
  })

  it('disables submit when no value', () => {
    render(<MarkOlderReadDialog {...defaultProps} />)
    expect(screen.getByText('Mark as read')).toBeDisabled()
  })

  it('does not render when closed', () => {
    render(<MarkOlderReadDialog {...defaultProps} open={false} />)
    expect(screen.queryByText('Mark as read')).not.toBeInTheDocument()
  })

  it('shows feed name', () => {
    render(<MarkOlderReadDialog {...defaultProps} />)
    expect(screen.getByText(/Test Feed/)).toBeInTheDocument()
  })
})
