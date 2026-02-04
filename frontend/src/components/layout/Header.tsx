import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { useTeam } from '../../contexts/TeamContext';
import { getUserInvites, getTeamMembers } from '../../services/team';
import TeamSwitcher from '../team/TeamSwitcher';
import CreateTeamModal from '../team/CreateTeamModal';
import InviteModal from '../team/InviteModal';
import TeamMembersModal from '../team/TeamMembersModal';
import APIKeysManager from '../APIKeysManager';

export default function Header() {
  const { user, logout } = useAuth();
  const { teams, currentTeam, setCurrentTeam, createTeam } = useTeam();
  const navigate = useNavigate();

  const [showCreateTeam, setShowCreateTeam] = useState(false);
  const [showInvite, setShowInvite] = useState(false);
  const [showMembers, setShowMembers] = useState(false);
  const [showAPIKeys, setShowAPIKeys] = useState(false);
  const [showUserMenu, setShowUserMenu] = useState(false);
  const [pendingInvitesCount, setPendingInvitesCount] = useState(0);
  const [isTeamOwner, setIsTeamOwner] = useState(false);

  useEffect(() => {
    loadPendingInvites();
    // Refresh every 30 seconds
    const interval = setInterval(loadPendingInvites, 30000);
    return () => clearInterval(interval);
  }, []);

  // Check if current user is owner of current team
  useEffect(() => {
    const checkOwnership = async () => {
      if (!currentTeam || !user) {
        setIsTeamOwner(false);
        return;
      }
      try {
        const members = await getTeamMembers(currentTeam.id);
        const currentMember = members.find((m) => m.user_id === user.id);
        setIsTeamOwner(currentMember?.role === 'owner');
      } catch (error) {
        console.error('Failed to check team ownership:', error);
        setIsTeamOwner(false);
      }
    };
    checkOwnership();
  }, [currentTeam?.id, user?.id]);

  const loadPendingInvites = async () => {
    try {
      const invites = await getUserInvites();
      setPendingInvitesCount(invites.length);
    } catch (error) {
      console.error('Failed to load invites count:', error);
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const handleCreateTeam = async (name: string) => {
    const newTeam = await createTeam(name);
    setCurrentTeam(newTeam);
  };

  return (
    <>
      <header className="bg-gradient-to-r from-blue-50 to-indigo-50 border-b-2 border-blue-500 px-6 py-4 flex justify-between items-center flex-shrink-0">
        <div>
          <h1 className="text-2xl font-bold text-blue-600">PostmanXodja</h1>
          <p className="text-gray-600 text-sm">API Testing Tool - Postman Collection Compatible</p>
        </div>

        <div className="flex items-center gap-4">
          <TeamSwitcher
            teams={teams}
            currentTeam={currentTeam}
            onTeamChange={setCurrentTeam}
            onCreateTeam={() => setShowCreateTeam(true)}
          />

          {currentTeam && (
            <>
              <button
                onClick={() => setShowMembers(true)}
                className="px-3 py-2 text-sm text-gray-600 hover:bg-gray-100 rounded-lg transition-colors flex items-center gap-1"
                title="View team members"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
                </svg>
                Members
              </button>
              {isTeamOwner && currentTeam.name !== 'Personal' && (
                <>
                  <button
                    onClick={() => setShowInvite(true)}
                    className="px-3 py-2 text-sm text-blue-600 hover:bg-blue-50 rounded-lg transition-colors flex items-center gap-1"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
                    </svg>
                    Invite
                  </button>
                  <button
                    onClick={() => setShowAPIKeys(true)}
                    className="px-3 py-2 text-sm text-gray-600 hover:bg-gray-100 rounded-lg transition-colors flex items-center gap-1"
                    title="Manage API Keys"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z" />
                    </svg>
                    API Keys
                  </button>
                </>
              )}
            </>
          )}

          {/* Invites notification bell */}
          <button
            onClick={() => navigate('/invites')}
            className="relative p-2 text-gray-600 hover:bg-white/50 rounded-lg transition-colors"
            title="Team Invites"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
            </svg>
            {pendingInvitesCount > 0 && (
              <span className="absolute -top-1 -right-1 bg-red-500 text-white text-xs rounded-full w-5 h-5 flex items-center justify-center font-medium">
                {pendingInvitesCount > 9 ? '9+' : pendingInvitesCount}
              </span>
            )}
          </button>

          <div className="relative">
            <button
              onClick={() => setShowUserMenu(!showUserMenu)}
              className="flex items-center gap-2 px-3 py-2 hover:bg-white/50 rounded-lg transition-colors"
            >
              <div className="w-8 h-8 bg-blue-500 rounded-full flex items-center justify-center text-white text-sm font-medium">
                {user?.name?.charAt(0).toUpperCase() || user?.email?.charAt(0).toUpperCase()}
              </div>
              <span className="text-sm text-gray-700 hidden sm:block">{user?.name || user?.email}</span>
              <svg className="w-4 h-4 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </button>

            {showUserMenu && (
              <>
                <div className="fixed inset-0 z-10" onClick={() => setShowUserMenu(false)} />
                <div className="absolute right-0 mt-2 w-48 bg-white rounded-lg shadow-lg border border-gray-200 z-20">
                  <div className="px-4 py-3 border-b border-gray-100">
                    <p className="text-sm font-medium text-gray-800">{user?.name}</p>
                    <p className="text-xs text-gray-500 truncate">{user?.email}</p>
                  </div>
                  <div className="py-1">
                    <button
                      onClick={() => {
                        setShowUserMenu(false);
                        navigate('/invites');
                      }}
                      className="w-full px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-50 flex items-center justify-between"
                    >
                      <span>My Invites</span>
                      {pendingInvitesCount > 0 && (
                        <span className="bg-red-500 text-white text-xs rounded-full px-2 py-0.5 min-w-[20px] text-center">
                          {pendingInvitesCount}
                        </span>
                      )}
                    </button>
                    <button
                      onClick={handleLogout}
                      className="w-full px-4 py-2 text-left text-sm text-red-600 hover:bg-red-50"
                    >
                      Sign out
                    </button>
                  </div>
                </div>
              </>
            )}
          </div>
        </div>
      </header>

      <CreateTeamModal
        isOpen={showCreateTeam}
        onClose={() => setShowCreateTeam(false)}
        onSubmit={handleCreateTeam}
      />

      {currentTeam && (
        <>
          <InviteModal
            isOpen={showInvite}
            onClose={() => setShowInvite(false)}
            teamId={currentTeam.id}
            teamName={currentTeam.name}
          />
          <TeamMembersModal
            isOpen={showMembers}
            onClose={() => setShowMembers(false)}
            teamId={currentTeam.id}
            teamName={currentTeam.name}
            isOwner={isTeamOwner}
          />
          {/* API Keys Modal */}
          {showAPIKeys && (
            <div className="fixed inset-0 flex items-center justify-center z-50">
              <div className="fixed inset-0 bg-black/50" onClick={() => setShowAPIKeys(false)} />
              <div className="relative bg-white rounded-lg w-full max-w-2xl max-h-[80vh] overflow-y-auto mx-4 shadow-xl">
                <div className="sticky top-0 bg-white border-b px-4 py-3 flex justify-between items-center">
                  <h2 className="text-lg font-semibold">API Keys - {currentTeam.name}</h2>
                  <button
                    onClick={() => setShowAPIKeys(false)}
                    className="p-1 hover:bg-gray-100 rounded"
                  >
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
                <APIKeysManager />
              </div>
            </div>
          )}
        </>
      )}
    </>
  );
}
