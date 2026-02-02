import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { getUserInvites, acceptInvite, declineInvite } from '../services/team';
import { useTeam } from '../contexts/TeamContext';
import type { TeamInvite } from '../types';

export default function InvitesPage() {
  const [invites, setInvites] = useState<TeamInvite[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { refreshTeams, setCurrentTeam } = useTeam();
  const navigate = useNavigate();

  useEffect(() => {
    loadInvites();
  }, []);

  const loadInvites = async () => {
    try {
      const data = await getUserInvites();
      setInvites(data);
    } catch (error) {
      console.error('Failed to load invites:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleAccept = async (invite: TeamInvite) => {
    if (!invite.token) return;
    setActionLoading(invite.token);
    setError(null);
    try {
      const result = await acceptInvite(invite.token);
      await refreshTeams();
      // Switch to the newly joined team and redirect to dashboard
      setCurrentTeam(result.team);
      navigate('/');
    } catch (err: any) {
      console.error('Failed to accept invite:', err);
      setError(err.message || 'Failed to accept invite');
      setActionLoading(null);
    }
  };

  const handleDecline = async (invite: TeamInvite) => {
    if (!invite.token) return;
    setActionLoading(invite.token);
    setError(null);
    try {
      await declineInvite(invite.token);
      setInvites((prev) => prev.filter((i) => i.id !== invite.id));
    } catch (err: any) {
      console.error('Failed to decline invite:', err);
      setError(err.message || 'Failed to decline invite');
    } finally {
      setActionLoading(null);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-2xl mx-auto py-8 px-4">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-bold text-gray-800">Team Invites</h1>
          <button
            onClick={() => navigate('/')}
            className="text-blue-600 hover:underline text-sm"
          >
            Back to Dashboard
          </button>
        </div>

        {error && (
          <div className="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
            {error}
          </div>
        )}

        {loading ? (
          <div className="text-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500 mx-auto mb-4"></div>
            <p className="text-gray-600">Loading invites...</p>
          </div>
        ) : invites.length === 0 ? (
          <div className="bg-white rounded-lg shadow p-8 text-center">
            <svg
              className="w-16 h-16 text-gray-300 mx-auto mb-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.5}
                d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"
              />
            </svg>
            <p className="text-gray-600">No pending invites</p>
          </div>
        ) : (
          <div className="space-y-4">
            {invites.map((invite) => (
              <div key={invite.id} className="bg-white rounded-lg shadow p-4">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="font-medium text-gray-800">{invite.team?.name}</h3>
                    <p className="text-sm text-gray-500">
                      Invited by {invite.inviter?.name || invite.inviter?.email}
                    </p>
                  </div>
                  <div className="flex gap-2">
                    <button
                      onClick={() => handleDecline(invite)}
                      disabled={actionLoading === invite.token}
                      className="px-3 py-1 text-sm text-gray-600 hover:bg-gray-100 rounded transition-colors disabled:opacity-50"
                    >
                      Decline
                    </button>
                    <button
                      onClick={() => handleAccept(invite)}
                      disabled={actionLoading === invite.token}
                      className="px-3 py-1 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors disabled:opacity-50"
                    >
                      {actionLoading === invite.token ? 'Joining...' : 'Accept'}
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
